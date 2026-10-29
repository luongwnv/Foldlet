#!/usr/bin/env python3
"""Rebuild the approved Foldlet Glance mark, original lettering and Android vectors."""
from pathlib import Path
OUT=Path(__file__).resolve().parent/'final'
BLUE='#3289FF'; YELLOW='#FFE14A'; INK='#173B5A'
def rect(x,y,w,h,r):
    return f'M{x+r} {y}H{x+w-r}Q{x+w} {y} {x+w} {y+r}V{y+h-r}Q{x+w} {y+h} {x+w-r} {y+h}H{x+r}Q{x} {y+h} {x} {y+h-r}V{y+r}Q{x} {y} {x+r} {y}Z'
def circle(x,y,r): return f'M{x} {y-r}a{r} {r} 0 1 0 0 {2*r}a{r} {r} 0 1 0 0 {-2*r}Z'
SHELL=rect(40,24,176,208,32)+' '+rect(56,40,144,176,16)+' '+circle(76,76,8)+' '+circle(76,112,8)+' '+rect(56,144,144,8,4)
SCREEN=rect(100,68,88,48,10)+' '+''.join(circle(x,92,6) for x in (120,144,168))
SMALL=rect(40,24,176,208,32)+' '+rect(60,44,136,168,12)+' '+circle(78,76,10)+' '+circle(78,112,10)+' '+rect(60,144,136,12,6)
SMALL_SCREEN=rect(108,68,80,48,10)
GLYPHS={
'F':(38,'M0 0H38V10H10V27H34V37H10V64H0Z'),
'O':(44,'M10 0H34Q44 0 44 10V54Q44 64 34 64H10Q0 64 0 54V10Q0 0 10 0Z M13 10Q10 10 10 13V51Q10 54 13 54H31Q34 54 34 51V13Q34 10 31 10Z'),
'L':(40,'M0 0H10V54H40V64H0Z'),
'D':(44,'M0 0H24Q44 0 44 20V44Q44 64 24 64H0Z M10 10V54H23Q34 54 34 43V21Q34 10 23 10Z'),
'E':(38,'M0 0H38V10H10V27H34V37H10V54H38V64H0Z'),
'T':(44,'M0 0H44V10H27V64H17V10H0Z')}
def lettering(color):
    x=0; parts=[]
    for char in 'FOLDLET':
        width,path=GLYPHS[char]; parts.append(f'<path transform="translate({x} 0)" fill="{color}" fill-rule="evenodd" d="{path}"/>'); x+=width+10
    return ''.join(parts)
def paths(body,screen,small=False):
    return f'<path fill="{body}" fill-rule="evenodd" d="{SMALL if small else SHELL}"/><path fill="{screen}" fill-rule="evenodd" d="{SMALL_SCREEN if small else SCREEN}"/>'
def svg(name,w,h,body,title):
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/(name+'.svg')).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" role="img" aria-labelledby="title"><title id="title">Foldlet — {title}</title>{body}</svg>\n')
def main():
    for name,body,screen in [('brand',BLUE,YELLOW),('on-blue','#FFFFFF',YELLOW),('black','#000000','#000000'),('white','#FFFFFF','#FFFFFF')]:
        symbol=paths(body,screen)
        svg('symbol-'+name,256,256,symbol,'Glance '+name)
        wordcolor=INK if name=='brand' else body
        svg('wordmark-'+name,384,112,'<g transform="translate(18 24)">'+lettering(wordcolor)+'</g>','wordmark '+name)
        svg('horizontal-'+name,832,256,symbol+'<g transform="translate(272 80) scale(1.5)">'+lettering(wordcolor)+'</g>','horizontal '+name)
        svg('stacked-'+name,512,416,'<g transform="translate(128 0)">'+symbol+'</g><g transform="translate(82 304)">'+lettering(wordcolor)+'</g>','stacked '+name)
    svg('symbol-small',256,256,paths(INK,INK,True),'small-size monochrome')
    svg('app-tile',256,256,f'<rect width="256" height="256" rx="56" fill="{BLUE}"/><g transform="translate(12.8 12.8) scale(0.9)">{paths("#FFFFFF",YELLOW)}</g>','app icon')
    # design/foldlet is two levels below the repository root.
    res=Path(__file__).resolve().parents[2]/'app/src/main/res/drawable'
    header='<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="256" android:viewportHeight="256">\n'
    group='    <group android:scaleX="0.6" android:scaleY="0.6" android:pivotX="128" android:pivotY="128">\n'
    def vector_path(path,color): return f'        <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{path}"/>\n'
    (res/'ic_launcher_foreground.xml').write_text(header+group+vector_path(SHELL,'#FFFFFF')+vector_path(SCREEN,YELLOW)+'    </group>\n</vector>\n')
    (res/'ic_launcher_monochrome.xml').write_text(header+group+vector_path(SHELL,'#FFFFFF')+vector_path(SCREEN,'#FFFFFF')+'    </group>\n</vector>\n')
    (res/'ic_status.xml').write_text(header.replace('108dp','24dp')+vector_path(SMALL,'#FFFFFF')+vector_path(SMALL_SCREEN,'#FFFFFF')+'</vector>\n')
if __name__=='__main__': main()
