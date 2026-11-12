#!/usr/bin/env python3
"""Regenerates the exercise illustrations and their mappings from a clone of everkinetic/data.

    python3 tools/exercise_images/build.py /path/to/everkinetic/data/src/exercises

Reads (nothing is downloaded):
  * the starter -> slug map in core/model/.../StarterMachines.kt (`images`, edited by hand),
  * the starter -> library id links in core/model/.../StarterLibraryLinks.kt,
  * LIBRARY below: library exercise id -> Everkinetic slug (each pair was checked by eye: same movement,
    same equipment class, same position/variant; doubtful pairs were left out),
  * the library in core/data/src/main/assets/exercise_library.json (to check ids).
Writes:
  * core/ui/src/main/assets/exercises/<slug>-1.webp and -2.webp (start / end frame; RGBA over white, thumbnail 360,
    centred on a 360x360 white canvas, WebP quality 80),
  * core/model/.../LibraryExerciseImages.kt (library id -> slug; library entries linked to a starter with a picture
    take the starter's slug),
  * docs/exercise-images/mapping.json (starters), library-mapping.json, unmatched.txt and the table in README.md.
`candidates.py` is the token/equipment matcher that proposed the pairs; it is only used to find candidates to review.
"""
import json
import os
import re
import sys

from PIL import Image

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
CLONE = sys.argv[1] if len(sys.argv) > 1 else '/home/user/everkinetic/data/src/exercises'
MODEL = f'{REPO}/core/model/src/main/kotlin/com/boratreino/core/model'
OUT = f'{REPO}/core/ui/src/main/assets/exercises'
DOCS = f'{REPO}/docs/exercise-images'

LIBRARY = {
    "Air_Bike": "air-bike",
    "Alternate_Hammer_Curl": "alternating-hammer-curl-with-dumbbell",
    "Alternate_Incline_Dumbbell_Curl": "alternating-incline-curl-with-dumbbell",
    "Back_Flyes_-_With_Bands": "back-flys-exercise-band",
    "Barbell_Ab_Rollout": "ab-rollout-with-barbell",
    "Barbell_Ab_Rollout_-_On_Knees": "ab-rollout-on-knees-with-barbell",
    "Barbell_Curls_Lying_Against_An_Incline": "lying-incline-curl-with-barbell",
    "Barbell_Hack_Squat": "hack-squat-with-barbell",
    "Barbell_Lunge": "barbell-lunges",
    "Barbell_Rear_Delt_Row": "rear-deltoid-row-barbell",
    "Barbell_Seated_Calf_Raise": "seated-calf-raise-with-barbell",
    "Barbell_Shrug": "barbell-shrugs",
    "Barbell_Side_Split_Squat": "side-squats-with-barbell",
    "Barbell_Squat_To_A_Bench": "squat-to-bench-with-barbell",
    "Barbell_Step_Ups": "step-ups-with-barbell",
    "Bent-Arm_Barbell_Pullover": "bent-arm-pullover",
    "Bent-Arm_Dumbbell_Pullover": "dumbbell-bent-arm-pullover",
    "Bent-Knee_Hip_Raise": "bent-knee-hip-raise",
    "Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench": "bent-over-rear-deltoid-raise-with-head-on-bench",
    "Bent_Over_Low-Pulley_Side_Lateral": "bent-over-lateral-cable-raises",
    "Cable_Hammer_Curls_-_Rope_Attachment": "hammer-curls-with-rope-and-cable",
    "Cable_Hip_Adduction": "hip-adduction",
    "Cable_Incline_Pushdown": "incline-pushdown-with-cable",
    "Cable_Incline_Triceps_Extension": "incline-triceps-extension-with-cable",
    "Cable_Internal_Rotation": "internal-cable-rotation",
    "Cable_Lying_Triceps_Extension": "lying-triceps-extension-with-cable",
    "Cable_One_Arm_Tricep_Extension": "one-arm-tricep-extension-with-cable",
    "Cable_Preacher_Curl": "preacher-curl-with-cable",
    "Cable_Seated_Crunch": "seated-ab-crunch-with-cable",
    "Cable_Shrugs": "cable-shoulder-shrugs",
    "Calf_Press_On_The_Leg_Press_Machine": "calves-press-on-leg-machine",
    "Calf_Raises_-_With_Bands": "calf-raises-with-band",
    "Close-Grip_EZ_Bar_Curl": "close-grip-ez-bar-curl-with-barbell",
    "Close-Grip_Standing_Barbell_Curl": "close-grip-standing-biceps-curls-with-barbell",
    "Cross-Body_Crunch": "cross-body-crunch",
    "Cross_Body_Hammer_Curl": "cross-body-hammer-curl-with-dumbbell",
    "Crunch_-_Legs_On_Exercise_Ball": "crunches-with-legs-on-stability-ball",
    "Decline_Close-Grip_Bench_To_Skull_Crusher": "decline-close-grip-bench-to-skull-crusher",
    "Decline_Crunch": "decline-crunch",
    "Decline_Dumbbell_Bench_Press": "decline-dumbbell-bench-press",
    "Decline_Dumbbell_Flyes": "decline-dumbbell-flys",
    "Decline_Dumbbell_Triceps_Extension": "decline-triceps-extension-with-dumbbell",
    "Decline_EZ_Bar_Triceps_Extension": "decline-ez-bar-triceps-extension-with-barbell",
    "Decline_Oblique_Crunch": "decline-oblique-crunch",
    "Dip_Machine": "tricep-dips",
    "Donkey_Calf_Raises": "donkey-calf-raises",
    "Drag_Curl": "drag-curl-with-barbell",
    "Dumbbell_Bicep_Curl": "biceps-curl-with-dumbbell",
    "Dumbbell_Lunges": "dumbbell-lunges",
    "Dumbbell_Lying_One-Arm_Rear_Lateral_Raise": "lying-one-arm-rear-lateral-raise",
    "Dumbbell_Lying_Rear_Lateral_Raise": "lying-rear-lateral-raise",
    "Dumbbell_One-Arm_Shoulder_Press": "one-arm-dumbbell-shoulder-press",
    "Dumbbell_One-Arm_Triceps_Extension": "one-arm-triceps-extension-with-dumbbell",
    "Dumbbell_One-Arm_Upright_Row": "one-arm-upright-row",
    "Dumbbell_Prone_Incline_Curl": "prone-incline-biceps-curl-with-dumbbell",
    "Dumbbell_Rear_Lunge": "rear-lunges-with-dumbbell",
    "Dumbbell_Seated_One-Leg_Calf_Raise": "seated-one-leg-calf-raise-with-dumbbell",
    "Dumbbell_Shrug": "shoulder-shrugs",
    "Dumbbell_Side_Bend": "side-bend-with-dumbbell",
    "Dumbbell_Squat": "squats-using-dumbbells",
    "Dumbbell_Squat_To_A_Bench": "squat-to-bench-with-dumbbells",
    "EZ-Bar_Skullcrusher": "lying-triceps-press-with-barbell",
    "Exercise_Ball_Crunch": "stability-ball-abdominal-crunch",
    "Exercise_Ball_Pull-In": "exercise-ball-pull-in",
    "Flat_Bench_Cable_Flyes": "flat-bench-cable-flys",
    "Flat_Bench_Lying_Leg_Raise": "flat-bench-leg-raises",
    "Flexor_Incline_Dumbbell_Curls": "flexor-incline-curls-with-dumbbell",
    "Flutter_Kicks": "flutter-kicks",
    "Front_Barbell_Squat_To_A_Bench": "front-squat-to-bench-with-barbells",
    "Front_Cable_Raise": "front-cable-raises",
    "Front_Raise_And_Pullover": "barbell-front-raise-pullover",
    "Front_Squat_Clean_Grip": "front-squat-with-barbell",
    "Hammer_Grip_Incline_DB_Bench_Press": "hammer-grip-incline-bench-press",
    "Incline_Barbell_Triceps_Extension": "incline-triceps-extension-with-barbell",
    "Incline_Cable_Flye": "incline-cable-flys",
    "Incline_Dumbbell_Flyes": "incline-dumbbell-flys",
    "Incline_Dumbbell_Flyes_-_With_A_Twist": "incline-flys-twist",
    "Incline_Inner_Biceps_Curl": "incline-inner-biceps-curl-with-dumbbell-2",
    "Inverted_Row": "body-row",
    "Iron_Cross": "iron-cross-with-dumbbells",
    "Isometric_Neck_Exercise_-_Front_And_Back": "static-neck-flexion-extension",
    "Isometric_Neck_Exercise_-_Sides": "static-neck-side-flexion",
    "JM_Press": "jm-press",
    "Jefferson_Squats": "jefferson-squats-with-barbell",
    "Kneeling_Cable_Triceps_Extension": "kneeling-triceps-extension-with-cable-2",
    "Leg_Lift": "body-leg-lifts",
    "Leverage_Chest_Press": "machine-bench-press",
    "Leverage_Decline_Chest_Press": "decline-chest-press",
    "Low_Cable_Triceps_Extension": "low-triceps-extension-with-cable",
    "Lying_Cable_Curl": "lying-bicep-cable-curl",
    "Lying_Close-Grip_Bar_Curl_On_High_Pulley": "lying-close-grip-biceps-curls-with-cable",
    "Lying_Close-Grip_Barbell_Triceps_Extension_Behind_The_Head": "lying-close-grip-triceps-extension-behind-the-head-with-barbell",
    "Lying_Close-Grip_Barbell_Triceps_Press_To_Chin": "lying-close-grip-triceps-press-to-chin-with-barbell",
    "Lying_High_Bench_Barbell_Curl": "lying-high-bench-biceps-curl-with-barbell",
    "Lying_Machine_Squat": "lying-squat",
    "Lying_Rear_Delt_Raise": "lying-rear-lateral-raise",
    "Lying_Supine_Dumbbell_Curl": "lying-supine-biceps-curl-with-dumbbell",
    "Lying_Triceps_Press": "lying-triceps-press-with-barbell",
    "Machine_Preacher_Curls": "preacher-curl-with-machine",
    "Narrow_Stance_Hack_Squats": "narrow-stance-hack-squats",
    "Narrow_Stance_Leg_Press": "narrow-stance-leg-press",
    "Narrow_Stance_Squats": "narrow-stance-squat-with-barbell",
    "Neck_Press": "barbell-neck-press",
    "One-Arm_Flat_Bench_Dumbbell_Flye": "one-arm-flat-bench-flys",
    "One-Arm_Side_Deadlift": "one-arm-side-deadlift-with-barbell",
    "One_Arm_Dumbbell_Bench_Press": "one-arm-bench-press",
    "One_Arm_Dumbbell_Preacher_Curl": "one-arm-preacher-curl-with-dumbbell",
    "One_Arm_Floor_Press": "one-arm-barbell-floor-press",
    "One_Arm_Pronated_Dumbbell_Triceps_Extension": "single-arm-triceps-extension-with-dumbbell",
    "One_Arm_Supinated_Dumbbell_Triceps_Extension": "single-arm-supinated-triceps-extension-with-dumbbell",
    "Overhead_Cable_Curl": "overhead-curl-with-cable",
    "Overhead_Squat": "overhead-squat-with-barbell",
    "Plie_Dumbbell_Squat": "pile-squat-with-dumbbell",
    "Preacher_Hammer_Dumbbell_Curl": "preacher-hammer-curl-with-dumbbell",
    "Push-Ups_-_Close_Triceps_Position": "close-triceps-pushup",
    "Push-Ups_With_Feet_Elevated": "push-up-feet-elevated",
    "Push-Ups_With_Feet_On_An_Exercise_Ball": "push-ups-with-feet-on-exercise-ball",
    "Reverse_Grip_Bent-Over_Rows": "reverse-grips-bent-over-barbell-rows",
    "Reverse_Grip_Triceps_Pushdown": "reverse-grip-triceps-pushdown",
    "Reverse_Plate_Curls": "reverse-plate-curls-with-weight",
    "Reverse_Triceps_Bench_Press": "reverse-triceps-bench-press-with-barbell",
    "Rocking_Standing_Calf_Raise": "rocking-standing-calf-raise-with-barbell",
    "Seated_Barbell_Military_Press": "seated-military-press",
    "Seated_Close-Grip_Concentration_Barbell_Curl": "seated-close-grip-concentration-curls-with-barbell",
    "Seated_Dumbbell_Curl": "seated-biceps-curl-with-dumbbell",
    "Seated_Dumbbell_Inner_Biceps_Curl": "seated-inner-biceps-curl-with-dumbbell",
    "Seated_Triceps_Press": "seated-triceps-press-with-dumbbell",
    "Smith_Machine_Bench_Press": "smith-machine-bench-press",
    "Smith_Machine_Close-Grip_Bench_Press": "smith-machine-close-grip-bench-press",
    "Smith_Machine_Incline_Bench_Press": "smith-machine-incline-bench-press",
    "Smith_Machine_Reverse_Calf_Raises": "smith-machine-reverse-calf-raises",
    "Smith_Machine_Upright_Row": "smith-machine-upright-row",
    "Speed_Squats": "speed-squats-with-barbell",
    "Spider_Curl": "spider-curl-with-barbell",
    "Squats_-_With_Bands": "squats-with-exercise-bands",
    "Standing_Barbell_Calf_Raise": "standing-barbell-calf-raise",
    "Standing_Bent-Over_One-Arm_Dumbbell_Triceps_Extension": "bent-over-one-arm-triceps-extension-with-dumbbell",
    "Standing_Bent-Over_Two-Arm_Dumbbell_Triceps_Extension": "bent-over-two-arm-triceps-extension-with-dumbbell",
    "Standing_Dumbbell_Reverse_Curl": "biceps-curl-reverse-with-dumbbells",
    "Standing_Dumbbell_Triceps_Extension": "standing-triceps-extension-2",
    "Standing_Dumbbell_Upright_Row": "dumbbell-upright-rows",
    "Standing_Inner-Biceps_Curl": "standing-inner-biceps-curl-with-dumbbell",
    "Standing_Low-Pulley_One-Arm_Triceps_Extension": "one-arm-low-pulley-triceps-extension-with-cable",
    "Standing_One-Arm_Cable_Curl": "standing-one-arm-biceps-curl-with-cable",
    "Standing_One-Arm_Dumbbell_Curl_Over_Incline_Bench": "standing-one-arm-curl-over-incline-bench-with-dumbbell",
    "Standing_Overhead_Barbell_Triceps_Extension": "standing-overhead-triceps-extension-with-barbell",
    "Standing_Towel_Triceps_Extension": "standing-triceps-extension-with-towel",
    "Straight-Arm_Dumbbell_Pullover": "straight-arm-dumbbell-pullover",
    "Tate_Press": "tate-press-with-dumbbell",
    "Tricep_Dumbbell_Kickback": "triceps-kickback-with-dumbbell",
    "Triceps_Pushdown_-_V-Bar_Attachment": "triceps-pushdown-with-v-bar-and-cable",
    "Two-Arm_Dumbbell_Preacher_Curl": "two-arm-preacher-curl-with-dumbbell",
    "Underhand_Cable_Pulldowns": "underhand-pull-downs",
    "Upright_Barbell_Row": "upright-barbell-rows",
    "Upright_Cable_Row": "upright-cable-row",
    "Upright_Row_-_With_Bands": "upright-band-rows",
    "V-Bar_Pulldown": "v-bar-pull-down",
    "Weighted_Ball_Side_Bend": "weighted-ball-side-bend",
    "Weighted_Sissy_Squat": "weighted-sissy-squat-with-weight-plate",
    "Wide-Grip_Barbell_Bench_Press": "wide-grip-bench-press",
    "Wide-Grip_Decline_Barbell_Bench_Press": "wide-grip-decline-bench-press",
    "Wide-Grip_Decline_Barbell_Pullover": "wide-grip-decline-barbell-pullover",
    "Wide-Grip_Standing_Barbell_Curl": "wide-grip-standing-biceps-curl-with-barbell",
    "Wide_Stance_Barbell_Squat": "wide-stance-squat-with-barbell",
    "Zercher_Squats": "zecher-squats",
    "Zottman_Curl": "zottman-curl-with-dumbbells",
    "Zottman_Preacher_Curl": "zottman-preacher-curl-with-dumbbells",
}


def read_clone():
    ex = {}
    for folder in sorted(os.listdir(CLONE)):
        for name in os.listdir(f'{CLONE}/{folder}'):
            if name.endswith('.json'):
                data = json.load(open(f'{CLONE}/{folder}/{name}'))
                # Some exercises have no pictures, and a few titles exist twice: keep the one with both frames.
                if all(os.path.exists(f'{CLONE}/{folder}/{folder}-{k}.png') for k in ('relaxation', 'tension')):
                    ex[data['name']] = (folder, data['title'])
    return ex


def convert(path):
    im = Image.open(path).convert('RGBA')
    bg = Image.new('RGBA', im.size, 'white')
    bg.alpha_composite(im)
    im = bg.convert('RGB')
    im.thumbnail((360, 360), Image.LANCZOS)
    canvas = Image.new('RGB', (360, 360), 'white')
    canvas.paste(im, ((360 - im.width) // 2, (360 - im.height) // 2))
    return canvas


def kotlin_pairs(text, start_marker):
    block = text[text.index(start_marker):]
    block = block[:block.index('\n    )')]
    return re.findall(r'"([^"]+)" to "([^"]+)"', block)


def main():
    ex = read_clone()
    starters_kt = open(f'{MODEL}/StarterMachines.kt').read()
    starter_map = dict(kotlin_pairs(starters_kt, 'private val images: Map<String, String> = mapOf('))
    names = re.findall(r'^\s+(?:w|bw)\("([^"]+)"', starters_kt, re.M)
    links = dict(kotlin_pairs(open(f'{MODEL}/StarterLibraryLinks.kt').read(), 'val ids: Map<String, String> = mapOf('))
    library = {e['id'] for e in json.load(open(f'{REPO}/core/data/src/main/assets/exercise_library.json'))}
    assert all(i in library for i in LIBRARY), [i for i in LIBRARY if i not in library]
    assert all(n in names for n in starter_map), [n for n in starter_map if n not in names]

    library_map = {links[s]: slug for s, slug in starter_map.items() if s in links}
    library_map.update(LIBRARY)
    slugs = sorted(set(starter_map.values()) | set(library_map.values()))
    os.makedirs(OUT, exist_ok=True)
    for slug in slugs:
        folder = ex[slug][0]
        for frame, kind in ((1, 'relaxation'), (2, 'tension')):
            convert(f'{CLONE}/{folder}/{folder}-{kind}.png').save(f'{OUT}/{slug}-{frame}.webp', 'WEBP', quality=80)

    with open(f'{DOCS}/mapping.json', 'w') as f:
        json.dump({n: starter_map[n] for n in names if n in starter_map}, f, indent=2, ensure_ascii=False)
        f.write('\n')
    with open(f'{DOCS}/library-mapping.json', 'w') as f:
        json.dump(dict(sorted(library_map.items())), f, indent=1)
        f.write('\n')
    with open(f'{DOCS}/unmatched.txt', 'w') as f:
        f.write('\n'.join(n for n in names if n not in starter_map) + '\n')

    readme = open(f'{DOCS}/README.md').read()
    head = readme[:readme.index('| Starter | Everkinetic title | Slug |')]
    rows = '\n'.join(f'| {n} | {ex[starter_map[n]][1].strip()} | `{starter_map[n]}` |' for n in names if n in starter_map)
    open(f'{DOCS}/README.md', 'w').write(head + '| Starter | Everkinetic title | Slug |\n|---|---|---|\n' + rows + '\n')

    body = '\n'.join(f'{k} {v}' for k, v in sorted(library_map.items()))
    open(f'{MODEL}/LibraryExerciseImages.kt', 'w').write(f'''package com.boratreino.core.model

/**
 * Generated by `tools/exercise_images/build.py`; do not edit by hand. Library exercise id to the slug of its
 * Everkinetic illustration (assets `<slug>-1.webp` and `<slug>-2.webp`). Several ids may share one slug.
 */
object LibraryExerciseImages {{
    private val slugs: Map<String, String> by lazy {{
        RAW.lineSequence().filter {{ it.isNotBlank() }}.associate {{ line -> line.substringBefore(' ') to line.substringAfter(' ') }}
    }}

    /** The picture slug of the library exercise [libraryId]; null when it has none. */
    fun slugFor(libraryId: String): String? = slugs[libraryId]

    private const val RAW = """
{body}
"""
}}
''')
    print(f'{len(slugs)} pictures, {len(library_map)} library entries, {len(starter_map)} starters')


if __name__ == '__main__':
    main()
