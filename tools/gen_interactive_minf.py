"""Build runtime research metadata from the supplied JSON and SHA-verified original BARS.

No audio or BARS binary is copied into the application. An unparsed grid remains
absent with a reason; partial-pair layouts are never rounded down or guessed.
"""
import argparse
import hashlib
import json
import struct
import subprocess
from pathlib import Path


def build(folder):
    j=json.loads((folder/'MKW_MINF_全量相位和命名切换事件数据_2026-11-11.json').read_text())
    bars={}
    for source in j['sources']:
        f=folder/source['sourceFile']
        if hashlib.sha256(f.read_bytes()).hexdigest()!=source['compressedSha256']:
            raise ValueError('Compressed BARS changed: '+f.name)
        raw=subprocess.check_output(['zstd','-dqcf',str(f)])
        if hashlib.sha256(raw).hexdigest()!=source['decompressedSha256']:
            raise ValueError('Decompressed BARS changed: '+f.name)
        bars[f.name]=raw
    records=[]
    for r in j['sourceRows']:
        off=r['MINF起始偏移(字节)'];size=r['MINF块大小(字节)']
        block=bars[r['BARS文件']][off:off+size]
        if hashlib.sha256(block).hexdigest()!=r['原始MINF_SHA256']:
            raise ValueError('MINF changed: '+r['MINF原始资源名称'])
        events=[{'name':e['标记原始名称'],'frames':e['源采样位置']} for e in j['namedEvents']
                if e['原始MINF_SHA256']==r['原始MINF_SHA256'] and e['MINF起始偏移(HEX)']==r['MINF起始偏移(HEX)']]
        end=0x3c+struct.unpack_from('<I',block,0x3c)[0]
        beats=[];grid_status='UNPARSED_LAYOUT'
        if 0x60<=end<len(block) and (end-0x60)%8==0:
            pairs=[struct.unpack_from('<Ii',block,n) for n in range(0x60,end,8)]
            if all(label in (-4,-3,-2,-1,1,2,3,4) for _,label in pairs) and all(a[0]<b[0] for a,b in zip(pairs,pairs[1:])):
                beats=[{'frame':frame,'label':label} for frame,label in pairs]
                grid_status='SOURCE_MINF'
        if 'Pre' in r['音乐段'] and grid_status!='SOURCE_MINF':
            raise ValueError('A previously established Pre grid could not be reproduced')
        records.append(dict(stage=r['内部赛道ID'],role=r['音乐段'],name=r['MINF原始资源名称'],
            phase=r['相位偏移(samples)'],rate=r['采样率'],bpm=r['BPM原始值'],loopStart=r['循环起点(samples)'],loopEnd=r['循环终点(samples)'],
            minfHash=r['原始MINF_SHA256'],offset=off,archive=r['BARS文件'],events=events,beats=beats,gridEvidence=grid_status))
    return {'version':'2026-11-12','sources':j['sources'],'records':records}


if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('research_folder',type=Path);ap.add_argument('output',type=Path);a=ap.parse_args()
    a.output.write_text(json.dumps(build(a.research_folder),ensure_ascii=False,separators=(',',':')),encoding='utf-8')
