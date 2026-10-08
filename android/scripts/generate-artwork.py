"""Original Ready Gym artwork. Generates vectors + same-geometry PNG previews.
No remote assets. Pillow is only needed for PNG exports, not Android builds.
"""
from pathlib import Path
from xml.sax.saxutils import escape
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'app/src/main/res'
OUT = ROOT/'build/verification/artwork'
OUT.mkdir(parents=True, exist_ok=True)
CHARCOAL='#111513'; LIME='#D0FA69'; LIGHT='#C4CDCB'; MID='#737F7D'; DARK='#3F4947'
class Art:
    def __init__(self, size, name):
        self.size=size; self.name=name; self.paths=[]
        self.image=Image.new('RGBA',(size*4,size*4)); self.draw=ImageDraw.Draw(self.image)
    def path(self, data, color, points=None, width=None):
        if width:
            self.paths.append(f'<path android:pathData="{data}" android:strokeColor="{color}" android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="@android:color/transparent"/>')
            self.draw.line([(x*4,y*4) for x,y in points],fill=color,width=int(width*4),joint='curve')
            for x,y in [points[0],points[-1]]: self.draw.ellipse(((x-width/2)*4,(y-width/2)*4,(x+width/2)*4,(y+width/2)*4),fill=color)
        else:
            self.paths.append(f'<path android:pathData="{data}" android:fillColor="{color}"/>')
            if points: self.draw.polygon([(x*4,y*4) for x,y in points], fill=color)
    def polygon(self, pts, color):
        self.path('M'+ ' L'.join(f'{x},{y}' for x,y in pts)+' Z',color,pts)
    def rect(self,x,y,w,h,color): self.polygon([(x,y),(x+w,y),(x+w,y+h),(x,y+h)],color)
    def line(self,pts,color,width): self.path('M'+' L'.join(f'{x},{y}' for x,y in pts),color,pts,width)
    def circle(self,x,y,r,color):
        self.path(f'M{x-r},{y} a{r},{r} 0,1 0,{2*r},0 a{r},{r} 0,1 0,{-2*r},0',color)
        self.draw.ellipse(((x-r)*4,(y-r)*4,(x+r)*4,(y+r)*4),fill=color)
    def save(self, directory='drawable'):
        p=RES/directory; p.mkdir(parents=True,exist_ok=True)
        (p/f'{self.name}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{self.size}dp" android:height="{self.size}dp" android:viewportWidth="{self.size}" android:viewportHeight="{self.size}">\n'+'\n'.join(self.paths)+'\n</vector>\n')
        self.image.save(OUT/f'{self.name}.png')
# A recognisable push-up silhouette, not a stick-figure symbol.
a=Art(160,'equipment_bodyweight'); a.line([(16,130),(145,130)],DARK,2)
a.circle(125,65,11,LIGHT); a.polygon([(49,80),(65,65),(110,64),(115,78),(69,94)],LIGHT)
a.polygon([(56,77),(62,91),(34,122),(16,126),(18,119)],MID)
a.polygon([(99,72),(110,78),(105,105),(130,121),(126,130),(94,109)],LIGHT)
a.polygon([(70,76),(76,89),(62,123),(46,128),(42,121)],MID)
a.rect(124,124,17,6,LIGHT); a.line([(71,69),(104,69)],'#E3E9E7',2); a.save()
# Cast-iron dumbbell with paired hex plates, collars, knurled handle.
a=Art(160,'equipment_dumbbell'); a.line([(25,126),(139,126)],DARK,2)
a.rect(48,75,68,11,MID); a.rect(67,77,27,7,LIGHT)
for x in [68,73,78,83,88]: a.line([(x,78),(x+3,82)],MID,1)
for x in [25,104]:
    a.polygon([(x,57),(x+19,50),(x+30,58),(x+30,102),(x+13,110),(x,102)],DARK)
    a.polygon([(x,57),(x+13,64),(x+13,110),(x,102)],MID)
    a.polygon([(x+13,64),(x+30,58),(x+30,102),(x+13,110)],LIGHT)
    a.line([(x+17,68),(x+25,65)],'#E4EBE8',2)
a.rect(51,73,5,15,LIGHT); a.rect(99,73,5,15,LIGHT); a.save()
# Olympic barbell: long metal shaft, weight discs and narrow end sleeves.
a=Art(160,'equipment_barbell'); a.line([(13,130),(146,130)],DARK,2)
a.rect(8,82,144,7,MID); a.rect(47,83,66,4,LIGHT)
for x in [23,118]:
    a.rect(x,54,9,62,DARK); a.rect(x+9,48,13,74,LIGHT)
    a.rect(x+11,51,3,68,MID); a.rect(x+22,63,6,45,MID)
    a.line([(x+12,54),(x+18,54)],'#E4EBE8',2)
a.rect(17,80,5,12,LIGHT); a.rect(148,80,5,12,LIGHT); a.save()
# Cable machine: twin uprights, top pulley crossbeam, weight stack, cable and handle.
a=Art(160,'equipment_machine'); a.line([(25,135),(142,135)],DARK,3)
a.rect(32,22,8,109,MID); a.rect(120,22,8,109,LIGHT); a.rect(32,21,96,8,LIGHT)
a.polygon([(32,127),(15,137),(57,137),(55,130)],MID); a.polygon([(115,128),(105,137),(148,137),(134,128)],LIGHT)
a.rect(45,56,31,69,DARK)
for y in range(61,118,8): a.rect(49,y,24,5,MID)
a.line([(62,36),(62,118)],LIGHT,2); a.circle(63,33,6,MID); a.circle(108,33,6,MID)
a.line([(63,33),(108,33),(108,70)],LIGHT,2)
a.line([(99,79),(108,70),(117,79)],LIGHT,3); a.line([(99,79),(117,79)],LIGHT,3)
a.rect(87,107,28,7,MID); a.rect(98,113,5,16,LIGHT); a.save()
# Flat adaptive mark: dumbbell bar folds into a check. Keep all ink in safe zone.
a=Art(108,'ic_launcher_foreground')
a.line([(38,53),(47,53),(55,63),(74,42)],LIME,6)
a.line([(72,52),(76,52)],LIME,6)
for x in [27,76]:
    a.rect(x,40,6,28,LIME); a.rect(x+6,35,7,38,LIME)
a.save()
a=Art(108,'ic_launcher_background'); a.rect(0,0,108,108,CHARCOAL); a.save()
# Matching flat PNG icon on a charcoal squircle (preview, not device screenshot).
im=Image.new('RGBA',(432,432)); d=ImageDraw.Draw(im); d.rounded_rectangle((0,0,431,431),radius=96,fill=CHARCOAL)
im.alpha_composite(Image.open(OUT/'ic_launcher_foreground.png')); im.save(OUT/'ready-gym-icon.png')
for name in ['ic_launcher','ic_launcher_round']:
    p=RES/'mipmap-anydpi-v26'; p.mkdir(parents=True,exist_ok=True)
    (p/f'{name}.xml').write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>\n')
print('Generated six original vectors, two adaptive icon resources and PNG previews:',OUT)
