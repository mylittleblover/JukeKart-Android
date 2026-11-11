#include "api/AudioSourceApi.hpp"
#include <iostream>
#include <vector>
using namespace player2;
void put(std::vector<std::uint8_t>& b,std::size_t at,std::uint32_t v,int n=4){for(int i=0;i<n;++i)b[at+i]=v>>(8*i);}
std::vector<std::uint8_t> source(bool padded,bool junk) {
    std::vector<std::uint8_t> b(padded ? 136 : 130);
    b[0]='B';b[1]='W';b[2]='A';b[3]='V';put(b,4,0xfeff,2);put(b,6,1,2);put(b,14,1,2);
    put(b,16,1,2);put(b,20,48000);put(b,24,1);put(b,28,1);
    put(b,64,128);put(b,68,128);put(b,72,1);put(b,76,0xffffffff);
    if(padded)b.back()=0x10; // unused byte in the final complete 14-sample DSP block
    if(junk)b.push_back(0x10); // outside the encoded block: still forbidden
    return b;
}
int main() {
    for(bool padded:{false,true})for(bool junk:{false,true}) {
        if(!padded && junk)continue;
        auto bytes=source(padded,junk);api::SourceOpenRequest request;
        request.providerId="native.bwav";request.bytes={bytes.data(),bytes.size()};
        const auto opened=api::openAudioSource(request);
        const auto expected=junk ? audio::SourceStatus::UnsupportedCodec : audio::SourceStatus::Ok;
        if(opened.status!=expected){std::cerr<<"padded="<<padded<<" junk="<<junk<<" expected="<<int(expected)<<" actual="<<int(opened.status)<<"\n";return 1;}
        if(opened.handle) {
            float pcm[1]={99};auto result=api::renderAudioSource(opened.handle,pcm,1,1);
            api::closeAudioSource(opened.handle);
            if(result.frames!=1 || pcm[0]!=0){std::cerr<<"Padding leaked into decoded PCM\n";return 1;}
        }
    }
    std::cout<<"PASS: tight/padded final DSP blocks; external nonzero trailer rejected\n";
}
