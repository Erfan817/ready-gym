"""Capture real Java/PostgreSQL contract replies with fictional data only."""
import asyncio
import json
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'agent-python'))
from ready_gym_foundation import JavaBusinessClient
from ready_gym_intake import Session, view

async def main():
    values=dict(line.split('=',1) for line in (Path.home()/'.config/ready-gym/business.env').read_text().splitlines() if line)
    client=JavaBusinessClient(values['READY_GYM_INTERNAL_TOKEN'])
    sid='captured_java_contract_fixture'
    profile={'age':30,'height_cm':170.0,'weight_kg':65.0,'gender':None,'goal':'muscle','location':'home','equipment':['dumbbell'],'days_per_week':3,'minutes_per_session':30,'experience':'beginner','current_discomfort':'none','medical_restrictions':'none'}
    await client.call('/internal/sessions/'+sid,method='DELETE')
    try:
        await client.call('/internal/profile/confirm',{'session_id':sid,'profile':profile,'profile_revision':1,'confirmed':True})
        draft=await client.call('/internal/tools/propose_plan',{'session_id':sid})
        session=Session(token_digest='fixture-never-authenticated',profile=profile,revision=1,confirmed=True,foundation_enabled=True)
        session.foundation_state={'plan_proposal':draft,'active_plan':None}
        replies=[view(session,sid)]
        await client.call('/internal/plan/confirm',{'session_id':sid,'draft_id':draft['id'],'expected_revision':1,'confirmed':True})
        active=await client.call('/internal/tools/get_profile',{'session_id':sid})
        session.foundation_state={**active,'plan_proposal':active['pending_plan']}
        replies.append(view(session,sid))
        revision=await client.call('/internal/tools/propose_revision',{'session_id':sid,'expected_revision':1,'days_per_week':4,'unavailable_days':[3]})
        session.foundation_state={**active,'plan_proposal':revision}
        replies.append(view(session,sid))
        dest=ROOT/'android/app/src/test/resources/foundation-real-java-replies.json'
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_text(json.dumps({'provenance':'Captured actual Java17/PostgreSQL HTTP replies; profile is fictional, no model/key/tokens', 'replies':replies},ensure_ascii=False,indent=2)+'\n')
        print('CAPTURED_REAL_JAVA_REPLY_CONTRACTS',len(replies))
    finally:
        await client.call('/internal/sessions/'+sid,method='DELETE')

if __name__=='__main__':asyncio.run(main())
