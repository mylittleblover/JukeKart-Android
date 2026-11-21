#pragma once
#include "audio/streaming/SourcePageCache.hpp"
#include <bitset>
#include <algorithm>
#include <cmath>
namespace player2::audio {
// Bounded page reservations for the one Runtime, not another playback scheduler.
// Retiring reservations survive to frame end, letting new Voices acquire their own
// leases before queued/replaced plans release old preparation pages.
class PreparationSet {
public:
    struct Requirement {SourcePageCache* cache{};int asset{-1};SourceSamplePosition sample;double fraction{};std::optional<SourceSamplePosition> nextSample{};};
    static constexpr std::size_t capacity=128;
private:
    struct Key {SourcePageCache* cache{};int asset{-1};std::int64_t first{-1},next{-1};
        bool operator==(const Key& b)const noexcept{return cache==b.cache && asset==b.asset && first==b.first && next==b.next;}};
    struct LeaseSlot {Key key;Requirement requirement;StreamCursor cursor;bool prepared{};bool requestedOnce{};std::uint32_t lastDecoded{};};
    std::array<LeaseSlot,capacity> slots_{};std::bitset<capacity> occupied_,wanted_;
    std::array<Requirement,capacity> configuredInput_{};
    std::array<std::size_t,capacity> wantedSlots_{};
    std::array<std::size_t,capacity> slotForInput_{};
    std::size_t configuredCount_{},wantedCount_{};
    bool configurationCached_{};
    static bool slotReady(LeaseSlot& s)noexcept {
        if(s.prepared)return true;
        const std::uint32_t decoded=s.key.cache->decodedCount();
        if(s.requestedOnce && !s.key.cache->controlRendering() && decoded==s.lastDecoded)return false;
        s.lastDecoded=decoded;
        s.requestedOnce=true;
        s.prepared=s.cursor.prepare(static_cast<double>(s.requirement.sample.value)+s.requirement.fraction,s.requirement.nextSample);
        return s.prepared;
    }
    static bool same(const Requirement& a,const Requirement& b)noexcept {
        return a.cache==b.cache && a.asset==b.asset && a.sample.value==b.sample.value && a.fraction==b.fraction &&
            a.nextSample.has_value()==b.nextSample.has_value() && (!a.nextSample || a.nextSample->value==b.nextSample->value);
    }
    static Key key(const Requirement& r)noexcept {
        const auto first=r.sample.value/static_cast<std::int64_t>(SourcePageCache::FramesPerPage);
        const auto last=r.cache->duration(r.asset).value-1;
        const auto next=(r.fraction==0 ? r.sample.value : (r.nextSample ? r.nextSample->value : std::min(r.sample.value+1,last)))/static_cast<std::int64_t>(SourcePageCache::FramesPerPage);
        return {r.cache,r.asset,first,next};
    }
public:
    struct FrameEnd {PreparationSet* set{};~FrameEnd(){if(set)set->trim();}};
    bool configure(const Requirement* input,std::size_t count)noexcept {
        if(count>capacity || (count && !input))return false;
        // Stable queued transitions reserve the same source pages for thousands of frames.
        // Reuse that exact configuration; changed positions/rates/selection still take the full path.
        if(configurationCached_ && count==configuredCount_) {
            bool unchanged=true;
            for(std::size_t n=0;n<count && unchanged;++n)unchanged=same(input[n],configuredInput_[n]);
            if(unchanged)return true;
        }
        std::array<Key,capacity> unique{};std::array<std::size_t,capacity> indexes{};std::array<std::size_t,capacity> uniqueForInput{};std::size_t size=0,needed=0;
        for(std::size_t n=0;n<count;++n) {
            const auto& r=input[n];
            if(!r.cache || r.asset<0 || r.sample.value<0 || r.sample.value>=r.cache->duration(r.asset).value || !std::isfinite(r.fraction) || r.fraction<0 || r.fraction>=1 || (r.nextSample && (r.nextSample->value<0 || r.nextSample->value>=r.cache->duration(r.asset).value)))return false;
            const auto k=key(r);std::size_t i=0;for(;i<size;++i)if(unique[i]==k)break;
            if(i<size){uniqueForInput[n]=i;continue;}
            unique[size]=k;indexes[size]=n;uniqueForInput[n]=size++;
            bool found=false;for(std::size_t s=0;s<capacity;++s)if(occupied_[s] && slots_[s].key==k){found=true;break;}
            needed+=!found;
        }
        if(needed>capacity-occupied_.count())return false; // Old reservations remain intact on refusal.
        std::bitset<capacity> wanted;
        for(std::size_t n=0;n<size;++n) {
            std::size_t slot=0;for(;slot<capacity;++slot)if(occupied_[slot] && slots_[slot].key==unique[n])break;
            if(slot==capacity) {
                for(slot=0;slot<capacity && occupied_[slot];++slot){}
                auto& s=slots_[slot];s.key=unique[n];s.requirement=input[indexes[n]];s.cursor.bind(s.key.cache,s.key.asset);s.prepared=false;s.requestedOnce=false;s.lastDecoded=0;occupied_.set(slot);
            }
            wanted.set(slot);wantedSlots_[n]=slot;
        }
        wanted_=wanted;wantedCount_=size;
        for(std::size_t n=0;n<count;++n){configuredInput_[n]=input[n];slotForInput_[n]=wantedSlots_[uniqueForInput[n]];}
        configuredCount_=count;configurationCached_=true;return true;
    }
    std::size_t size()const noexcept{return wanted_.count();}
    bool ready()noexcept {
        bool ready=true;
        for(std::size_t index=0;index<wantedCount_;++index) {
            if(!slotReady(slots_[wantedSlots_[index]]))ready=false;
        }
        return ready;
    }
    bool readyRange(std::size_t begin,std::size_t end)noexcept {
        bool ready=true;
        const auto limit=std::min(end,configuredCount_);
        for(std::size_t index=begin;index<limit;++index) {
            if(!slotReady(slots_[slotForInput_[index]]))ready=false;
        }
        return ready;
    }
    void retire()noexcept{wanted_.reset();wantedCount_=0;configuredCount_=0;configurationCached_=false;}
    void trim()noexcept{if(occupied_==wanted_)return;for(std::size_t n=0;n<capacity;++n)if(occupied_[n] && !wanted_[n]){slots_[n].cursor.reset();slots_[n].prepared=false;slots_[n].requestedOnce=false;slots_[n].lastDecoded=0;occupied_.reset(n);}}
};
}
