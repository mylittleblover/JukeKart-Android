import pytest
from tools.build_interactive_pack import create_tracks


def row(stage, role, name, n):
    return dict(stage=stage, role=role, fileName=name, sha256=f'{n:032x}'*2)


def test_fast_is_a_separate_v1_track_in_the_same_stage_group():
    tracks, stages = create_tracks([
        row('FirstCircuit', 'Main', 'BGM_Stage_FirstCircuit.bwav', 1),
        row('FirstCircuit', 'Fast', 'BGM_Stage_FirstCircuit_Fast.bwav', 2),
    ])
    assert len(tracks) == 2
    assert len(stages) == 1 and len(stages[0]['tracks']) == 2
    assert all(t['parts'][0]['role'] == 'MAIN' for t in tracks)
    assert all(len(t['parts']) == 1 for t in tracks)


def test_pattern2_keeps_its_own_main_and_reuses_only_confirmed_base_outros():
    tracks, stages = create_tracks([
        row('Cinema', 'Main', 'BGM_Stage_Cinema.bwav', 1),
        row('Cinema', 'Outro', 'BGM_Stage_Cinema_Outro00.bwav', 2),
        row('Cinema_Pattern2', 'Main', 'BGM_Stage_Cinema_Pattern2.bwav', 3),
        row('Cinema_Pattern2', 'Fast', 'BGM_Stage_Cinema_Fast_Pattern2.bwav', 4),
    ])
    pattern = next(t for t in tracks if t['primary'] == f'{3:032x}'*2)
    assert [(p['role'], p['asset']) for p in pattern['parts']] == [('MAIN', f'{3:032x}'*2), ('OUTRO', f'{2:032x}'*2)]
    assert len(stages) == 1


def test_rainbow_declares_all_regions_as_single_file_tracks():
    roles = ['Area1_Pre', 'Area1_Bridge', 'Area1_Inst', 'Area1_Melody', 'Area2',
             *[f'Area2_Outro{n:02}' for n in range(5)], 'Area3_Pre', 'Area3', 'Final_Pre', 'Final', 'FinalLapFanfare']
    tracks, stages = create_tracks([row('Rainbow', 'Main', f'BGM_Rainbow_{r}.bwav', n+1) for n, r in enumerate(roles)])
    assert len(tracks) == 15 and len(stages[0]['tracks']) == 15
    assert all(not t['isFlow'] and t['parts'][0]['role'] == 'MAIN' for t in tracks)
    with pytest.raises(ValueError, match='Rainbow'):
        create_tracks([row('Rainbow', 'Main', 'BGM_Rainbow_Area1_Inst.bwav', 1)])


def test_snow_pattern2_does_not_acquire_unconfirmed_outro06_from_its_base():
    tracks, _ = create_tracks([
        row('SnowMountain', 'Main', 'BGM_Stage_SnowMountain.bwav', 1),
        row('SnowMountain', 'Outro', 'BGM_Stage_SnowMountain_Outro00.bwav', 2),
        row('SnowMountain', 'Outro', 'BGM_Stage_SnowMountain_Outro06.bwav', 3),
        row('SnowMountain_Pattern2', 'Main', 'BGM_Stage_SnowMountain_Pattern2.bwav', 4),
    ])
    pattern = next(t for t in tracks if t['primary'] == f'{4:032x}'*2)
    assert [p.get('outro') for p in pattern['parts'] if p['role'] == 'OUTRO'] == [0]
