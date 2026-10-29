#!/usr/bin/env python3
"""Verify real system-locale changes and the native settings drawer preview on the owned AVD."""
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

root=pathlib.Path(__file__).resolve().parents[1]
serial=None
for line in subprocess.check_output(['adb','devices'],text=True).splitlines():
    if line.startswith('emulator-'):
        candidate=line.split()[0]
        if 'LIT_Cover_API_36_1' in subprocess.check_output(['adb','-s',candidate,'emu','avd','name'],text=True).splitlines():
            serial=candidate; break
if serial is None: raise SystemExit('Start the dedicated LIT_Cover_API_36_1 AVD first')
adb=['adb','-s',serial]
def command(*args): return subprocess.check_output(adb+list(args),text=True,timeout=30).strip()
old_locale=command('shell','cmd','locale','get-device-locale')
old_size=command('shell','wm','size'); old_density=command('shell','wm','density')
out=root/'artifacts'/'locales'; out.mkdir(parents=True,exist_ok=True)
def nodes():
    command('shell','uiautomator','dump','/data/local/tmp/foldlet-locale.xml')
    return list(ET.fromstring(command('shell','cat','/data/local/tmp/foldlet-locale.xml')).iter('node'))
try:
    print(command('install','-r',str(root/'app/build/outputs/apk/debug/app-debug.apk')),flush=True)
    command('shell','wm','size','648x1536'); command('shell','wm','density','288')
    command('shell','input','keyevent','KEYCODE_WAKEUP'); command('shell','input','keyevent','KEYCODE_MENU')
    for locale,expected in [('vi-VN',('Tổng quan','Cài đặt','Thông tin')),('en-US',('Overview','Settings','About'))]:
        command('shell','cmd','locale','set-device-locale',locale)
        command('shell','am','force-stop','vn.lit.screenos')
        command('shell','am','start','-W','-n','vn.lit.screenos/.MainActivity')
        time.sleep(.8)
        tree=nodes(); labels={n.get('text') for n in tree}
        assert all(label in labels for label in expected),(locale,labels)
        with (out/f'overview-{locale}.png').open('wb') as f: subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True)
        button=next(n for n in tree if n.get('content-desc')==expected[1])
        left,top,right,bottom=map(int,re.findall(r'\d+',button.get('bounds')))
        command('shell','input','tap',str((left+right)//2),str((top+bottom)//2)); time.sleep(.6)
        settings_labels={n.get('text') for n in nodes()}
        subtitle='Cá nhân hóa menu màn hình ngoài' if locale=='vi-VN' else 'Personalize your cover screen menu'
        assert subtitle in settings_labels, (locale, 'Settings tap did not navigate', settings_labels)
        with (out/f'settings-{locale}.png').open('wb') as f: subprocess.run(adb+['exec-out','screencap','-p'],stdout=f,check=True)
        print('PASS: system locale',locale,'updates actual app tabs and settings',flush=True)
finally:
    command('shell','cmd','locale','set-device-locale',old_locale)
    size=re.search(r'Override size: (\d+x\d+)',old_size); density=re.search(r'Override density: (\d+)',old_density)
    command('shell','wm','size',size[1] if size else 'reset'); command('shell','wm','density',density[1] if density else 'reset')
    command('shell','rm','-f','/data/local/tmp/foldlet-locale.xml')
