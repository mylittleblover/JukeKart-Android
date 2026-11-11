"""Build runtime research metadata from the supplied JSON and SHA-verified original BARS.

No audio or BARS binary is copied into the application. An unparsed grid remains
absent with a reason; partial-pair layouts are never rounded down or guessed.
"""
import argparse
import hashlib
import json
import struct
import subprocess
import tempfile
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from mkw_tools.bars.reader import read_bars


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
    logical={}
    with tempfile.TemporaryDirectory() as scratch:
        for filename,raw in bars.items():
            path=Path(scratch)/filename.removesuffix('.zs');path.write_bytes(raw)
            logical[filename]=read_bars(str(path))
    records=[]
    for r in j['sourceRows']:
        off=r['MINF起始偏移(字节)'];size=r['MINF块大小(字节)']
        block=bars[r['BARS文件']][off:off+size]
        if hashlib.sha256(block).hexdigest()!=r['原始MINF_SHA256']:
            raise ValueError('MINF changed: '+r['MINF原始资源名称'])
        events=[{'name':e['标记原始名称'],'frames':e['源采样位置']} for e in j['namedEvents']
                if e['原始MINF_SHA256']==r['原始MINF_SHA256'] and e['MINF起始偏移(HEX)']==r['MINF起始偏移(HEX)']]
        end=0x3c+struct.unpack_from('<I',block,0x3c)[0]
        tempo_word=struct.unpack_from('<I',block,0x4c)[0]
        tempo_count=tempo_word&0xffff
        descriptor=0x50+12*tempo_count
        start=descriptor+4
        if not 1<=tempo_count<=64 or not start<=end<len(block):
            raise ValueError('Invalid MINF tempo/grid layout')
        beat_count=struct.unpack_from('<I',block,descriptor)[0]&0xffff
        if start+8*beat_count!=end:
            raise ValueError('MINF beat count/section boundary mismatch')
        pairs=[struct.unpack_from('<Ii',block,start+n*8) for n in range(beat_count)]
        if not all(label in (-4,-3,-2,-1,1,2,3,4) for _,label in pairs) or not all(a[0]<b[0] for a,b in zip(pairs,pairs[1:])):
            raise ValueError('Invalid MINF source grid')
        beats=[{'frame':frame,'label':label} for frame,label in pairs]
        tempos=[]
        for n in range(tempo_count):
            at=0x50+12*n
            frame,bpm,signature=struct.unpack_from('<IfI',block,at)
            tempos.append({'frame':frame,'bpm':bpm,'numerator':signature&0xffff,'denominator':signature>>16})
        matches=[e for e in logical[r['BARS文件']] if e.amta_offset<=off<e.amta_offset+e.declared_size]
        if len(matches)!=1:raise ValueError('MINF has no unique owning AMTA')
        owner=matches[0]
        grid_status='SOURCE_MINF'
        records.append(dict(stage=r['内部赛道ID'],role=r['音乐段'],name=r['MINF原始资源名称'],
            phase=r['相位偏移(samples)'],rate=r['采样率'],bpm=r['BPM原始值'],loopStart=r['循环起点(samples)'],loopEnd=r['循环终点(samples)'],
            minfHash=r['原始MINF_SHA256'],offset=off,archive=r['BARS文件'],events=events,beats=beats,gridEvidence=grid_status,
            logicalName=owner.internal_name,sourceAsset=owner.source_asset,amtaOrdinal=owner.ordinal,tempos=tempos,tempoFlags=tempo_word>>16))
    return {'version':'2026-11-12','sources':j['sources'],'records':records}


if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('research_folder',type=Path);ap.add_argument('output',type=Path);a=ap.parse_args()
    a.output.write_text(json.dumps(build(a.research_folder),ensure_ascii=False,separators=(',',':')),encoding='utf-8')
