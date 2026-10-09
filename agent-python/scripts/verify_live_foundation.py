"""Bounded opt-in live acceptance; sends only fictional adult demo data.

Uses private configuration without echoing any key, header or session token.
Max two retries are HUMAN-visible reruns of this program; no network auto retry.
"""
import argparse
import asyncio
import copy
import json
from pathlib import Path
import sys

import httpx

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'agent-python'))
from ready_gym_intake import DeepSeekAdapter, parse_private_config, create_app
from ready_gym_foundation import FoundationProvider, JavaBusinessClient

async def main():
    folder=Path.home()/'.config/ready-gym'
    key=parse_private_config((folder/'.env').read_text())
    values=dict(x.split('=',1) for x in (folder/'business.env').read_text().splitlines() if x)
    business=JavaBusinessClient(values['READY_GYM_INTERNAL_TOKEN'])
    app=create_app(provider=DeepSeekAdapter(key),business=business,agent_provider=FoundationProvider(key))
    report={'real_provider':True,'model':'deepseek-flash','business':'Java17/PostgreSQL','fictional_data_only':True,'stages':[]}
    sid=None
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://live-private') as client:
        def check(response):
            if response.status_code not in {200,201,204}:
                code=response.json().get('error',{}).get('code')
                raise RuntimeError('Live stage failed HTTP '+str(response.status_code)+' '+str(code))
            return response.json() if response.content else {}
        started=check(await client.post('/api/intake/sessions',json={'consent':True}))
        sid=started['session_id'];headers={'Authorization':'Bearer '+started['session_token']};root='/api/intake/sessions/'+sid
        report['stages'].append({'stage':'start','status':started['status'],'calls_used':started['calls_used']})
        print('LIVE_INITIAL_QUESTION_OK',flush=True)
        data=check(await client.post(root+'/messages',headers=headers,json={'message':'我年龄21岁，身高175厘米，体重70公斤，目标增肌，训练地点在家，器械为哑铃，每周3天，每次30分钟，训练经验新手'}))
        answers={'age':'我年龄21岁','height_cm':'我身高175厘米','weight_kg':'我体重70公斤',
            'goal':'我目标是增肌','location':'我地点在家','equipment':'我器械为哑铃',
            'days_per_week':'我每周3天','minutes_per_session':'我每次30分钟','experience':'我经验是新手',
            'gender':'我性别不透露','current_discomfort':'没有当前不适','medical_restrictions':'没有医学训练限制'}
        for _ in range(2):
            if data['status']=='ready' or data['status']=='blocked':break
            fields=data['missing_fields']
            print('LIVE_ACTUAL_MISSING_FIELDS',json.dumps(fields,ensure_ascii=False),flush=True)
            answer='，'.join(answers[k] for k in fields)
            data=check(await client.post(root+'/messages',headers=headers,json={'message':answer}))
        print('LIVE_PROFILE_STATUS',json.dumps({'status':data['status'],'missing_fields':data['missing_fields'],
            'clarification_fields':data['clarification_fields'],'safety_route':data['safety_route'],'calls_used':data['calls_used']},ensure_ascii=False),flush=True)
        if data['status']!='ready' or data['safety_route'] is not None:
            await client.delete(root,headers=headers)
            raise RuntimeError('Provider still needs profile clarification; no business confirmation; test session deleted')
        report['stages'].append({'stage':'profile_summary','status':data['status'],'calls_used':data['calls_used']})
        print('LIVE_INTAKE_SUMMARY_OK',flush=True)
        confirmed=check(await client.post(root+'/foundation/confirm-profile',headers=headers,json={'confirmed':True,'expected_revision':data['revision']}))
        print('LIVE_PLAN_STAGE_METADATA', json.dumps({'source':confirmed['agent_source'],'trace':confirmed['tool_trace'],
            'notice':confirmed['agent_notice'],'calls_used':confirmed['calls_used'],'usage':confirmed['usage']},ensure_ascii=False),flush=True)
        plan=confirmed['plan_proposal']
        if not plan or plan.get('status')!='draft':
            await client.delete(root,headers=headers)
            raise RuntimeError('No proposal within bounded model loop; no plan adopted; test session deleted')
        assert confirmed['agent_source']=='deepseek_tool_calls','This run fell back: not live Agent tool success'
        report['stages'].append({'stage':'real_tool_plan_draft','agent_source':confirmed['agent_source'],'tool_trace':confirmed['tool_trace'],'revision':plan['revision']})
        print('LIVE_AGENT_PLAN_TOOL_OK',flush=True)
        accepted=check(await client.post(root+'/foundation/confirm-plan',headers=headers,json={'confirmed':True,'draft_id':plan['id'],'expected_revision':plan['revision']}))
        assert accepted['active_plan']['status']=='confirmed' and accepted['active_plan']['revision']==1
        report['stages'].append({'stage':'confirmed_java_version1','revision':1})
        revised=check(await client.post(root+'/foundation/messages',headers=headers,json={'message':'周三没空，改成四天'}))
        v2=revised['plan_proposal'];assert v2 and v2['revision']==2 and v2['days_per_week']==4 and 3 not in v2['training_days']
        assert revised['active_plan']['revision']==1
        report['stages'].append({'stage':'real_natural_language_revision','tool_trace':revised['tool_trace'],'new_training_days':v2['training_days'],'old_active_revision':1})
        adopted=check(await client.post(root+'/foundation/confirm-plan',headers=headers,json={'confirmed':True,'draft_id':v2['id'],'expected_revision':2}))
        history=await business.call('/internal/tools/get_training_history',{'session_id':sid})
        assert [p['revision'] for p in history['versions']]==[1,2]
        assert adopted['active_plan']['revision']==2
        report['stages'].append({'stage':'java_persisted_versions','revisions':[1,2]})
        blocked=check(await client.post(root+'/foundation/messages',headers=headers,json={'message':'我现在胸痛'}))
        assert blocked['safety_route']=='stop_and_seek_care' and blocked['plan_proposal'] is None
        report['stages'].append({'stage':'risk_stop','route':'stop_and_seek_care','no_extra_model_call':blocked['calls_used']==adopted['calls_used']})
        report['usage']=blocked['usage'];report['calls_used']=blocked['calls_used'];report['completed']=True
        deleted=await client.delete(root,headers=headers);assert deleted.status_code==204
        assert (await client.get(root,headers=headers)).status_code==404
        report['demo_records_deleted_verified']=True
    dest=ROOT/'android/build/verification/v0.4.0/live-foundation-e2e.json'
    dest.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':
    asyncio.run(main())
