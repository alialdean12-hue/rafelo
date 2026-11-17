import sys
SKY="#24143a";INK="#140c1c";MOON="#ffd7a1";ACC="#ff4d6d";PRA="#ffb020";IRIS="#ffd0a1";SKIN="#f0c2ae";HAIR="#1a1020";SCL="#f6f3ee";LINE="#fff1ea"
F=1/24
def lantern(cx,cy,w=200,h=260,sy=1.0,lw=5):
    x=cx-w/2;y=cy-h/2
    ribs="".join(f'<path d="M{x+6},{y+h*k} Q{cx},{y+h*k+10} {x+w-6},{y+h*k}" fill="none" stroke="{INK}" stroke-width="3" opacity="0.35"/>' for k in (0.25,0.5,0.75))
    return (f'<g transform="translate({cx} {cy}) scale(1 {sy}) translate({-cx} {-cy})">'
      f'<path d="M{cx-w*0.25},{y-8} Q{cx},{y-70} {cx+w*0.25},{y-8}" fill="none" stroke="{LINE}" stroke-width="4"/>'
      f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{w*0.3}" fill="{PRA}" stroke="{LINE}" stroke-width="{lw}"/>'
      f'<path d="M{cx},{cy-h*0.26} C{cx+w*0.24},{cy} {cx+w*0.16},{cy+h*0.28} {cx},{cy+h*0.3} C{cx-w*0.16},{cy+h*0.28} {cx-w*0.24},{cy} {cx},{cy-h*0.26} Z" fill="{MOON}"/>'
      f'{ribs}'
      f'<rect x="{x+w*0.16}" y="{y-12}" width="{w*0.68}" height="24" rx="6" fill="{INK}" stroke="{LINE}" stroke-width="3"/>'
      f'<rect x="{x+w*0.16}" y="{y+h-12}" width="{w*0.68}" height="24" rx="6" fill="{INK}" stroke="{LINE}" stroke-width="3"/></g>')
def small_lantern(cx,cy,s=1.0,cls="",id_=""):
    w=34*s;h=46*s
    i=f' id="{id_}"' if id_ else ''
    return f'<g{i} class="{cls}"><rect x="{cx-w/2}" y="{cy-h/2}" width="{w}" height="{h}" rx="{w*0.3}" fill="{PRA}"/><rect x="{cx-w*0.2}" y="{cy-h*0.2}" width="{w*0.4}" height="{h*0.45}" rx="{w*0.15}" fill="{MOON}"/></g>'

def eye(cx,cy,ry,closed,r=30,rx=46,side=1):
    if closed:
        return f'<path d="M{cx-rx},{cy-4} Q{cx},{cy+18} {cx+rx},{cy-4}" fill="none" stroke="{HAIR}" stroke-width="8" stroke-linecap="round"/>'
    ry=ry+10
    return (f'<path d="M{cx-rx},{cy+4} Q{cx-rx*0.5},{cy-ry-4} {cx},{cy-ry-6} Q{cx+rx*0.6},{cy-ry-2} {cx+rx},{cy+2} Q{cx},{cy+ry+8} {cx-rx},{cy+4} Z" fill="{SCL}"/>'
            f'<ellipse cx="{cx}" cy="{cy-4}" rx="{r*0.82}" ry="{min(r,ry)}" fill="{IRIS}" stroke="{HAIR}" stroke-width="4"/>'
            f'<path d="M{cx-r*0.82},{cy-4} A{r*0.82},{min(r,ry)} 0 0 1 {cx+r*0.82},{cy-4} Z" fill="{INK}" opacity="0.2"/>'
            f'<ellipse cx="{cx}" cy="{cy-4}" rx="{r*0.36}" ry="{r*0.46}" fill="{HAIR}"/>'
            f'<rect x="{cx+4}" y="{cy-24}" width="12" height="14" fill="{LINE}"/><rect x="{cx-16}" y="{cy+6}" width="7" height="8" fill="{MOON}"/>'
            f'<path d="M{cx-rx-8*side-4},{cy+2} Q{cx-rx*0.5},{cy-ry-12} {cx},{cy-ry-12} Q{cx+rx*0.6},{cy-ry-8} {cx+rx+6},{cy-2}" fill="none" stroke="{HAIR}" stroke-width="10" stroke-linecap="round"/>'
            f'<path d="M{cx-rx*0.6},{cy-ry-36} Q{cx},{cy-ry-48} {cx+rx*0.6},{cy-ry-36}" fill="none" stroke="{HAIR}" stroke-width="5" stroke-linecap="round"/>')

def girl(id_,by=0,ly=None,sy=1.0,look=0.0,hands=None,sway=0,closed=False,mouth_open=False,speed=False):
    o=[f'<g id="{id_}" class="draw">']
    if speed:
        for x,y1,y2 in [(250,300,900),(330,150,700),(760,200,820),(840,380,960),(180,600,1100),(900,650,1080)]:
            o.append(f'<path d="M{x},{y1} L{x},{y2}" stroke="{LINE}" stroke-width="4" opacity="0.7"/>')
    o.append(f'<g transform="translate(0 {by})">')
    # hair back + bun
    o.append(f'<path d="M540,618 C760,618 770,820 752,960 Q744,1050 700,1100 L380,1100 Q336,1050 328,960 C310,820 320,618 540,618 Z" fill="{HAIR}" stroke="{LINE}" stroke-width="5" stroke-linejoin="round"/>')
    o.append(f'<circle cx="540" cy="618" r="72" fill="{HAIR}" stroke="{LINE}" stroke-width="5"/>')
    o.append(f'<path d="M470,560 L640,640" stroke="{ACC}" stroke-width="10" stroke-linecap="round"/><circle cx="470" cy="560" r="14" fill="{PRA}"/>')
    # side locks
    for s in (-1,1):
        x=540+s*190
        o.append(f'<path d="M{x-s*52},800 Q{x+s*30},860 {x+s*8+sway},1110 Q{x-s*30+sway},1080 {x-s*50+sway*0.5},1000 Q{x-s*36},900 {x-s*52},800 Z" fill="{HAIR}" stroke="{LINE}" stroke-width="4" stroke-linejoin="round"/>')
    # neck
    o.append(f'<rect x="496" y="990" width="88" height="130" fill="{SKIN}"/>')
    o.append(f'<path d="M496,1010 L584,1010 L584,1060 Q540,1090 496,1040 Z" fill="{INK}" opacity="0.2"/>')
    # torso
    o.append(f'<path d="M330,1190 Q340,1120 440,1100 L640,1100 Q740,1120 750,1190 L830,1960 L250,1960 Z" fill="{ACC}" stroke="{LINE}" stroke-width="6" stroke-linejoin="round"/>')
    o.append(f'<path d="M600,1100 L640,1100 Q740,1120 750,1190 L830,1960 L640,1960 Z" fill="{INK}" opacity="0.2"/>')
    o.append(f'<path d="M478,1098 L540,1250 L602,1098 Z" fill="{SKIN}"/>')
    o.append(f'<path d="M462,1096 L540,1262 L618,1096" fill="none" stroke="{LINE}" stroke-width="14" stroke-linejoin="round"/>')
    o.append(f'<path d="M289,1560 L791,1560 L806,1710 L274,1710 Z" fill="{PRA}" stroke="{LINE}" stroke-width="5" stroke-linejoin="round"/>')
    for px,py in [(380,1300),(700,1380),(430,1470),(660,1250),(350,1800),(740,1790),(540,1840)]:
        o.append(f'<circle cx="{px}" cy="{py}" r="14" fill="{MOON}"/><circle cx="{px}" cy="{py}" r="5" fill="{PRA}"/>')
    o.append(f'<path d="M280,1630 L800,1630" stroke="{ACC}" stroke-width="10"/>')
    # face
    fy=-look*42
    o.append(f'<path d="M392,800 C392,640 688,640 688,800 C688,900 650,960 606,1000 Q566,1034 540,1036 Q514,1034 474,1000 C430,960 392,900 392,800 Z" fill="{SKIN}" stroke="{LINE}" stroke-width="5"/>')
    o.append(f'<path d="M400,780 L680,780 L680,{838+fy*0.3} Q540,{800+fy*0.3} 400,{838+fy*0.3} Z" fill="{INK}" opacity="0.2"/>')
    ry=30-look*7
    o.append(eye(468,880+fy,ry,closed,side=1)); o.append(eye(612,880+fy,ry,closed,side=-1))
    o.append(f'<ellipse cx="430" cy="{948+fy}" rx="26" ry="12" fill="{ACC}" opacity="0.35"/><ellipse cx="650" cy="{948+fy}" rx="26" ry="12" fill="{ACC}" opacity="0.35"/>')
    if mouth_open:
        o.append(f'<ellipse cx="540" cy="{986+fy}" rx="12" ry="10" fill="{HAIR}"/>')
    else:
        o.append(f'<path d="M522,{984+fy} Q540,{996+fy} 558,{984+fy}" fill="none" stroke="{HAIR}" stroke-width="5" stroke-linecap="round"/>')
    # bangs
    o.append(f'<path d="M384,830 Q380,690 440,650 Q540,596 640,650 Q700,690 696,830 Q676,800 664,760 Q650,820 618,846 Q610,800 590,768 Q570,830 534,852 Q528,800 500,768 Q484,826 452,842 Q440,800 420,766 Q408,806 384,830 Z" fill="{HAIR}" stroke="{LINE}" stroke-width="4" stroke-linejoin="round"/>')
    o.append(f'<path d="M500,768 Q512,720 506,680 M590,768 Q582,720 592,684" fill="none" stroke="{LINE}" stroke-width="3" opacity="0.5"/>')
    o.append(f'<path d="M436,712 Q540,664 644,712 Q540,688 436,712 Z" fill="{MOON}"/>')
    o.append('</g>')
    if ly is not None:
        o.append(lantern(540,ly,sy=sy))
    # arms
    for s in (-1,1):
        if hands: hx=540+s*hands[0]; hy=hands[1]
        else: hx=540+s*128; hy=ly+50
        sx=540+s*205; syy=1150+by
        ax=540+s*190; ay=1330+by
        up = hy < syy
        drx=hx+s*(70 if up else 96); dry=hy+(230 if up else 190)
        d=(f"M{sx-s*34},{syy-28} L{hx+s*6},{hy-42} L{hx+s*44},{hy+36} "
           f"Q{hx+s*86},{hy+90} {drx},{dry} Q{(drx+ax)/2},{dry+34} {ax-s*26},{max(ay,dry-60) if not up else ay} Z")
        o.append(f'<path d="{d}" fill="{ACC}" stroke="{LINE}" stroke-width="6" stroke-linejoin="round"/>')
        o.append(f'<path d="M{hx+s*44},{hy+36} Q{hx+s*86},{hy+90} {drx},{dry} L{drx-s*34},{dry-10} Z" fill="{INK}" opacity="0.2"/>')
        o.append(f'<circle cx="{drx-s*40}" cy="{dry-70}" r="12" fill="{MOON}"/>')
        o.append(f'<circle cx="{hx}" cy="{hy}" r="36" fill="{SKIN}" stroke="{LINE}" stroke-width="5"/>')
    o.append('</g>')
    return "\n".join(o)

def sky(id_):
    return f'<defs><linearGradient id="{id_}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{INK}"/><stop offset="0.6" stop-color="{SKY}"/><stop offset="1" stop-color="{SKY}"/></linearGradient></defs><rect width="1080" height="1920" fill="url(#{id_})"/>'

# ---------- shot 1: establish ----------
stars=[(120,220),(300,140),(460,300),(620,160),(950,240),(200,520),(880,640),(70,760),(520,560),(1000,900),(360,820)]
s1=[sky("g1")]
s1+= [f'<rect x="{x}" y="{y}" width="5" height="5" fill="{LINE}" opacity="0.7"/>' for x,y in stars]
s1.append(f'<circle cx="790" cy="400" r="130" fill="{MOON}"/>')
# far hills + pagoda
s1.append(f'<path d="M0,1180 Q200,1040 420,1130 Q620,1030 820,1110 Q960,1060 1080,1140 L1080,1500 L0,1500 Z" fill="{INK}" opacity="0.55"/>')
s1.append(f'<g fill="{INK}" opacity="0.75"><path d="M150,1120 L150,1000 L110,1000 L190,940 L270,1000 L230,1000 L230,1120 Z"/><path d="M130,950 L190,890 L250,950 Z"/><rect x="186" y="850" width="8" height="50"/></g>')
# stalls
s1.append(f'<path d="M0,1500 L0,1330 L60,1290 L250,1290 L310,1330 L310,1380 L420,1380 L420,1310 L480,1270 L660,1270 L720,1310 L720,1390 L820,1390 L820,1320 L880,1284 L1040,1284 L1080,1310 L1080,1500 Z" fill="{INK}"/>')
for x,y in [(90,1380),(170,1380),(250,1380),(500,1360),(590,1360),(670,1360),(900,1370),(980,1370)]:
    s1.append(f'<rect x="{x-22}" y="{y}" width="44" height="60" fill="{PRA}" opacity="0.9"/>')
# lantern strings
s1.append(f'<path d="M0,1180 Q270,1300 540,1200 Q810,1100 1080,1220" fill="none" stroke="{LINE}" stroke-width="3" opacity="0.6"/>')
for i,(x,y) in enumerate([(90,1232),(200,1262),(310,1268),(420,1246),(540,1212),(660,1190),(780,1186),(900,1196),(1010,1216)]):
    s1.append(f'<ellipse cx="{x}" cy="{y}" rx="16" ry="20" fill="{ACC if i%2 else PRA}"/>')
for x,w in [(40,230),(440,260),(840,220)]:
    for k in range(0,w,40):
        s1.append(f'<rect x="{x+k}" y="1330" width="20" height="26" fill="{ACC}" opacity="0.85"/>')
s1.append(f'<g fill="{ACC}"><rect x="318" y="1392" width="12" height="110"/><rect x="398" y="1392" width="12" height="110"/><path d="M300,1384 Q364,1368 428,1384 L428,1398 L300,1398 Z"/><rect x="312" y="1416" width="104" height="10"/></g>')
for x in (740,762,790,330,356):
    s1.append(f'<circle cx="{x}" cy="1478" r="10" fill="{HAIR}"/><rect x="{x-9}" y="1486" width="18" height="30" rx="6" fill="{HAIR}"/>')
for x,y in [(60,90),(400,60),(700,110),(1010,420),(140,380),(610,420),(930,80)]:
    s1.append(f'<rect x="{x}" y="{y}" width="4" height="4" fill="{LINE}" opacity="0.6"/>')
# ground hill + girl
s1.append(f'<path d="M0,1920 L0,1640 Q300,1470 560,1500 Q840,1530 1080,1680 L1080,1920 Z" fill="{INK}" stroke="{SKY}" stroke-width="4"/>')
def tiny(id_,look,lit):
    hy=1352-look*4
    return (f'<g id="{id_}" class="draw"><path d="M508,1506 L522,1400 L558,1400 L572,1506 Z" fill="{ACC}"/><rect x="514" y="1440" width="52" height="16" fill="{PRA}"/>'
            f'<circle cx="540" cy="{hy+24}" r="26" fill="{HAIR}"/><circle cx="540" cy="{hy-2}" r="12" fill="{HAIR}"/>'
            f'<rect x="590" y="1408" width="34" height="46" rx="10" fill="{PRA}"/><rect x="598" y="1418" width="18" height="24" rx="6" fill="{MOON if lit else PRA}"/>'
            f'<path d="M556,1420 L596,1430" stroke="{ACC}" stroke-width="14" stroke-linecap="round"/></g>')
s1.append(tiny("e1",0,True)); s1.append(tiny("e2",0,False)); s1.append(tiny("e3",1,True))
FL=[(140,900),(330,1050),(470,760),(660,940),(900,1040),(240,640),(980,720),(560,1100),(760,560)]
s1.append('<g id="fl1">'+"".join(small_lantern(x,y,0.8+0.1*(i%3),cls="fl") for i,(x,y) in enumerate(FL))+'</g>')

# ---------- shot 2: sakuga ----------
s2=[sky("g2")]
s2+= [f'<rect x="{x}" y="{y}" width="6" height="6" fill="{LINE}" opacity="0.7"/>' for x,y in stars]
s2.append(f'<circle cx="880" cy="330" r="170" fill="{MOON}"/>')
s2.append(f'<path d="M0,1500 Q300,1420 540,1470 Q800,1520 1080,1440 L1080,1920 L0,1920 Z" fill="{INK}" opacity="0.6"/>')
for i,x in enumerate(range(40,1080,110)):
    s2.append(f'<ellipse cx="{x}" cy="{1330+(i%3)*18}" rx="18" ry="22" fill="{ACC if i%2 else PRA}" opacity="0.55"/>')
s2.append('<g id="fl2">'+"".join(small_lantern(x,y,1.2) for x,y in [(120,700),(260,380),(960,820),(150,1080),(930,1150)])+'</g>')
s2.append(girl("d1",by=0,ly=1300,look=0))
s2.append(girl("d2",by=36,ly=1372,look=0))
s2.append(girl("d3",by=-12,ly=1040,look=0.4,mouth_open=True))
s2.append(girl("d4",by=-26,ly=620,sy=1.9,look=0.8,mouth_open=True,speed=True,hands=(128,860)))
s2.append(girl("d5",by=-30,ly=400,look=1,mouth_open=True,hands=(128,560)))
s2.append(girl("d6",by=-18,look=1,hands=(240,640),sway=-14,mouth_open=True))
s2.append(girl("d7",by=0,look=1,hands=(270,1000),sway=8))
s2.append(girl("d8",by=0,look=1,hands=(270,1000),sway=0,closed=True))
s2.append(f'<g id="fly">{lantern(540,300)}</g>')

# ---------- shot 3: button close-up ----------
def big_eye(cx,cy,closed,side=1):
    if closed:
        return (f'<path d="M{cx-130},{cy-6} Q{cx},{cy+52} {cx+130},{cy-6}" fill="none" stroke="{HAIR}" stroke-width="18" stroke-linecap="round"/>'
                f'<path d="M{cx-side*120},{cy+4} l{-side*26},16 M{cx-side*96},{cy+18} l{-side*20},22" stroke="{HAIR}" stroke-width="8" stroke-linecap="round"/>')
    iy=cy-22
    return (f'<path d="M{cx-132},{cy+10} Q{cx-70},{cy-112} {cx},{cy-118} Q{cx+84},{cy-110} {cx+132},{cy+2} Q{cx+60},{cy+98} {cx},{cy+100} Q{cx-70},{cy+94} {cx-132},{cy+10} Z" fill="{SCL}"/>'
            f'<ellipse cx="{cx}" cy="{iy}" rx="80" ry="96" fill="{IRIS}" stroke="{HAIR}" stroke-width="7"/>'
            f'<path d="M{cx-80},{iy} A80,96 0 0 1 {cx+80},{iy} Q{cx},{iy+34} {cx-80},{iy} Z" fill="{INK}" opacity="0.2"/>'
            f'<ellipse cx="{cx}" cy="{iy+4}" rx="34" ry="46" fill="{HAIR}"/>'
            f'<path d="M{cx-40},{iy+62} Q{cx},{iy+84} {cx+40},{iy+62} Q{cx},{iy+72} {cx-40},{iy+62} Z" fill="{PRA}"/>'
            f'<rect x="{cx+14}" y="{iy-70}" width="34" height="42" fill="{LINE}"/><rect x="{cx-50}" y="{iy+20}" width="16" height="20" fill="{MOON}"/>'
            f'<path d="M{cx-150*1},{cy+14} Q{cx-74},{cy-124} {cx},{cy-132} Q{cx+88},{cy-124} {cx+146},{cy+2}" fill="none" stroke="{HAIR}" stroke-width="22" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="M{cx-side*140},{cy-10} l{-side*34},-22 M{cx-side*126},{cy-46} l{-side*28},-26" stroke="{HAIR}" stroke-width="10" stroke-linecap="round"/>'
            f'<path d="M{cx-70},{cy+112} Q{cx},{cy+124} {cx+70},{cy+110}" fill="none" stroke="{HAIR}" stroke-width="5" stroke-linecap="round" opacity="0.6"/>')
def close(id_,closed=False,sway=0,smile=0):
    o=[f'<g id="{id_}" class="draw"><g transform="translate(-30 40) rotate(-5 540 800)">']
    # back hair
    o.append(f'<path d="M-80,1700 L-60,420 Q540,-220 1140,420 L1160,1700 Z" fill="{HAIR}"/>')
    # neck + shadow
    o.append(f'<path d="M400,1240 L680,1240 L700,1700 L380,1700 Z" fill="{SKIN}"/><path d="M400,1290 L680,1290 L684,1400 Q560,1500 396,1380 Z" fill="{INK}" opacity="0.2"/>')
    # face
    o.append(f'<path d="M150,600 C150,240 930,240 930,600 C930,860 830,1060 720,1200 Q610,1340 540,1352 Q470,1340 360,1200 C250,1060 150,860 150,600 Z" fill="{SKIN}" stroke="{LINE}" stroke-width="6" stroke-linejoin="round"/>')
    # lantern rim light on the cheek (one practical light, flat)
    o.append(f'<path d="M930,600 C930,860 830,1060 720,1200 Q660,1276 612,1318 Q700,1200 790,1040 C860,900 900,760 930,600 Z" fill="{MOON}" opacity="0.75"/>')
    # under-bang shadow
    o.append(f'<path d="M150,500 L930,500 L930,650 Q540,560 150,650 Z" fill="{INK}" opacity="0.2"/>')
    o.append(big_eye(340,850,closed,1)); o.append(big_eye(740,850,closed,-1))
    o.append(f'<path d="M240,700 Q340,668 430,694" fill="none" stroke="{HAIR}" stroke-width="9" stroke-linecap="round"/><path d="M650,694 Q740,668 840,700" fill="none" stroke="{HAIR}" stroke-width="9" stroke-linecap="round"/>')
    o.append(f'<ellipse cx="236" cy="1030" rx="84" ry="36" fill="{ACC}" opacity="0.35"/><ellipse cx="844" cy="1030" rx="84" ry="36" fill="{ACC}" opacity="0.35"/>')
    for bx in (200,236,272,808,844,880):
        o.append(f'<path d="M{bx},1012 l-14,34" stroke="{ACC}" stroke-width="5" stroke-linecap="round" opacity="0.6"/>')
    o.append(f'<path d="M530,1040 L546,1064 L528,1066" fill="none" stroke="{INK}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" opacity="0.5"/>')
    o.append(f'<path d="M476,{1150} Q540,{1178+smile} 604,{1150}" fill="none" stroke="{HAIR}" stroke-width="8" stroke-linecap="round"/>')
    if smile: o.append(f'<path d="M508,{1196} Q540,{1206} 572,{1196}" fill="none" stroke="{INK}" stroke-width="4" stroke-linecap="round" opacity="0.4"/>')
    # bangs: curved chunks
    o.append(f'<path d="M110,760 Q90,360 250,230 Q540,40 830,230 Q990,360 970,720 Q940,640 910,540 Q880,620 800,660 Q780,590 730,520 Q690,630 590,680 Q570,600 510,530 Q460,630 370,665 Q350,590 300,520 Q250,640 110,720 Z" fill="{HAIR}" stroke="{LINE}" stroke-width="5" stroke-linejoin="round"/>')
    o.append(f'<path d="M510,530 Q524,450 506,380 M730,520 Q712,440 736,380 M300,520 Q308,450 344,390" fill="none" stroke="{LINE}" stroke-width="3" opacity="0.45"/>')
    o.append(f'<path d="M240,330 Q540,150 840,330 Q540,230 240,330 Z" fill="{MOON}"/>')
    for sd in (-1,1):
        x=540+sd*440
        o.append(f'<path d="M{x-sd*70},600 Q{x+sd*40},640 {x+sd*50+sway},1560 Q{x-sd*20+sway},1500 {x-sd*70+sway*0.5},1280 Q{x-sd*30},900 {x-sd*70},600 Z" fill="{HAIR}" stroke="{LINE}" stroke-width="5" stroke-linejoin="round"/>')
    # hairpin
    o.append(f'<path d="M800,150 L960,250" stroke="{ACC}" stroke-width="16" stroke-linecap="round"/><circle cx="800" cy="150" r="22" fill="{PRA}" stroke="{LINE}" stroke-width="4"/>')
    # collar
    o.append(f'<path d="M-80,1560 L400,1440 L540,1680 L680,1440 L1160,1560 L1160,2000 L-80,2000 Z" fill="{ACC}" stroke="{LINE}" stroke-width="6" stroke-linejoin="round"/>')
    o.append('</g></g>')
    return "\n".join(o)
s3=[sky("g3")]
s3.append('<g id="fl3">'+"".join(small_lantern(x,y,1.6) for x,y in [(90,260),(1000,180),(60,1250),(1020,1180)])+'</g>')
s3.append(close("b1")); s3.append(close("b2",sway=10,smile=10)); s3.append(close("b3",closed=True,smile=10))
s3.append(f'<rect x="0" y="1560" width="1080" height="360" fill="{INK}"/><rect x="0" y="0" width="1080" height="110" fill="{INK}"/>')

html=f'''<!doctype html>
<html lang="ar">
  <head>
    <meta charset="utf-8" />
    <title>Lantern — html-anime</title>
    <style>
      @font-face {{
        font-family: "Cairo";
        src: url("fonts/cairo-ar.woff2") format("woff2");
        font-weight: 200 1000;
        font-style: normal;
        font-display: block;
      }}
      html, body {{ margin: 0; background: {INK}; }}
      #root {{ position: relative; width: 1080px; height: 1920px; overflow: hidden; background: {INK}; }}
      .clip {{ position: absolute; inset: 0; overflow: hidden; }}
      .world {{ position: absolute; inset: 0; width: 1080px; height: 1920px; transform-origin: 50% 70%; }}
      .draw {{ opacity: 0; }}
      #e1, #d1, #b1 {{ opacity: 1; }}
      #fly {{ opacity: 0; }}
      #dip {{ background: {ACC}; opacity: 0; z-index: 4; }}
      #out {{ background: {INK}; opacity: 0; z-index: 5; }}
      #line {{
        position: absolute; left: 0; top: 1668px; width: 1080px; text-align: center; direction: rtl;
        font-family: "Cairo", sans-serif; font-weight: 700; font-size: 66px; color: {PRA}; z-index: 3;
      }}
    </style>
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
  </head>
  <body>
    <div id="root" data-composition-id="lantern" data-start="0" data-duration="8" data-width="1080" data-height="1920">
      <div id="s1" class="clip" data-start="0" data-duration="2.65" data-track-index="1">
        <svg id="w1" class="world" viewBox="0 0 1080 1920">
{chr(10).join(s1)}
        </svg>
      </div>
      <div id="s2" class="clip" data-start="2.4" data-duration="3.85" data-track-index="2">
        <svg class="world" viewBox="0 0 1080 1920">
{chr(10).join(s2)}
        </svg>
      </div>
      <div id="s3" class="clip" data-start="6" data-duration="2" data-track-index="1">
        <svg class="world" viewBox="0 0 1080 1920">
{chr(10).join(s3)}
        </svg>
        <div id="line">بعض الأمنيات تعرف طريقها.</div>
      </div>
      <div id="dip" class="clip" data-start="0" data-duration="8" data-track-index="3"></div>
      <audio id="mix" src="audio/mix.wav" data-start="0" data-duration="8" data-track-index="5" data-volume="1"></audio>
      <div id="out" class="clip" data-start="7.5" data-duration="0.5" data-track-index="4"></div>
    </div>
    <script>
      const F = 1 / 24;
      const tl = gsap.timeline({{ paused: true }});
      const swap = (from, to, frame) => {{
        tl.set(from, {{ opacity: 0 }}, frame * F);
        tl.set(to, {{ opacity: 1 }}, frame * F);
      }};

      // Shot 1 — establish: one camera move, three drawings, lanterns on ones.
      tl.fromTo("#w1", {{ scale: 1 }}, {{ scale: 1.06, duration: 2.4, ease: "power1.inOut" }}, 0);
      tl.to("#fl1", {{ y: -150, duration: 2.65, ease: "none" }}, 0);
      swap("#e1", "#e2", 14);
      swap("#e2", "#e1", 16);
      swap("#e1", "#e3", 36);

      // Cut 1 — color dip.
      tl.set("#dip", {{ opacity: 0 }}, 0);
      tl.to("#dip", {{ opacity: 1, duration: 0.25, ease: "power2.in" }}, 2.15);
      tl.to("#dip", {{ opacity: 0, duration: 0.35, ease: "power2.out" }}, 2.45);

      // Shot 2 — sakuga: camera locked, drawings swap on twos.
      tl.to("#fl2", {{ y: -220, duration: 3.85, ease: "none" }}, 2.4);
      swap("#d1", "#d2", 72);
      swap("#d2", "#d3", 76);
      swap("#d3", "#d4", 78);
      swap("#d4", "#d5", 80);
      swap("#d5", "#d6", 82);
      swap("#d6", "#d7", 86);
      swap("#d7", "#d8", 112);
      swap("#d8", "#d7", 114);
      tl.set("#fly", {{ opacity: 1 }}, 82 * F);
      tl.fromTo("#fly", {{ x: 0, y: 0 }}, {{ x: 46, y: -640, duration: 44 * F, ease: "none" }}, 82 * F);

      // Cut 2 — color dip.
      tl.to("#dip", {{ opacity: 1, duration: 0.25, ease: "power2.in" }}, 5.75);
      tl.to("#dip", {{ opacity: 0, duration: 0.35, ease: "power2.out" }}, 6.05);

      // Shot 3 — button: the line, a hold, fade the last 8 frames to ink.
      tl.to("#fl3", {{ y: -120, duration: 2, ease: "none" }}, 6);
      tl.from("#line", {{ y: 24, opacity: 0, duration: 0.4, ease: "power3.out" }}, 6.4);
      swap("#b1", "#b2", 158);
      swap("#b2", "#b3", 170);
      swap("#b3", "#b2", 172);
      tl.to("#out", {{ opacity: 1, duration: 8 * F, ease: "power1.in" }}, 184 * F);

      window.__timelines = window.__timelines || {{}};
      window.__timelines["lantern"] = tl;
    </script>
  </body>
</html>
'''
open(sys.argv[1],"w").write(html)
