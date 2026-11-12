"""Candidate generator shared by build.py: token overlap + equipment class between library/starter names and Everkinetic titles."""
import json, os, re

EK = '/home/user/everkinetic/data/src/exercises'

SYN = {'dumbbells': 'dumbbell', 'dumbell': 'dumbbell', 'db': 'dumbbell', 'flys': 'fly', 'flyes': 'fly', 'flye': 'fly',
       'fly\'s': 'fly', 'flies': 'fly', 'lunges': 'lunge', 'squats': 'squat', 'curls': 'curl', 'raises': 'raise',
       'rows': 'row', 'presses': 'press', 'extensions': 'extension', 'shrugs': 'shrug', 'crunches': 'crunch',
       'dips': 'dip', 'pushups': 'pushup', 'pullups': 'pullup', 'chinups': 'chinup', 'situps': 'situp',
       'kickbacks': 'kickback', 'deadlifts': 'deadlift', 'lifts': 'lift', 'thrusts': 'thrust', 'twists': 'twist',
       'steps': 'step', 'ups': 'up', 'pullovers': 'pullover', 'bridges': 'bridge', 'planks': 'plank',
       'rotations': 'rotation', 'swings': 'swing', 'cleans': 'clean', 'snatches': 'snatch', 'jerks': 'jerk',
       'good': 'good', 'mornings': 'morning', 'pulldowns': 'pulldown', 'pushdowns': 'pushdown',
       'two': '2', 'one': '1', 'single': '1', 'arms': 'arm', 'legs': 'leg', 'hands': 'hand', 'grips': 'grip',
       'kettlebells': 'kettlebell', 'bands': 'band', 'exercise': '', 'ez': 'ezbar', 'ezcurl': 'ezbar',
       'upright': 'upright', 'olympic': '', 'bar': 'bar', 'with': '', 'using': '', 'the': '', 'a': '', 'of': '', 'on': '',
       'and': '', 'to': '', 'for': '', 'in': ''}
EQUIP_WORDS = {'barbell', 'dumbbell', 'cable', 'machine', 'kettlebell', 'band', 'ezbar', 'smith', 'lever', 'leverage',
               'bodyweight', 'body', 'weight', 'weighted', 'plate', 'rope', 'bar', 'ball', 'medicine', 'stability', 'swiss',
               'bosu', 'bench', 'handle', 'attachment', 'resistance', 'tbar', 'v'}
VARIANT = {'incline', 'decline', 'seated', 'standing', 'lying', 'kneeling', 'reverse', 'wide', 'close', 'narrow', 'alternate',
           'alternating', '1', '2', 'single', 'overhead', 'front', 'rear', 'behind', 'bent', 'prone', 'supine', 'hammer',
           'underhand', 'overhand', 'neutral', 'sumo', 'stiff', 'straight', 'low', 'high', 'side', 'lateral', 'walking',
           'jump', 'jumping', 'isometric', 'static', 'hip', 'floor', 'flat', 'cross', 'crossover', 'unilateral', 'weighted',
           'assisted', 'hang', 'power', 'push', 'pull', 'inner', 'outer', 'twist', 'bosu', 'ball', 'stability', 'swiss',
           'medicine', 'band', 'rope', 'forward', 'backward', 'step', 'box', 'elevated', 'toe', 'heel', 'bodyweight', 'body'}


def toks(s):
    s = s.lower().replace("fly's", 'fly').replace('e-z', 'ez').replace('t-bar', 'tbar').replace('v-bar', 'v bar')
    s = s.replace('dead lift', 'deadlift').replace('pull up', 'pullup').replace('push up', 'pushup').replace('chin up', 'chinup')
    s = s.replace('sit up', 'situp').replace('push down', 'pushdown').replace('pull down', 'pulldown').replace('step up', 'stepup')
    s = s.replace('ez bar', 'ezbar').replace('ez-bar', 'ezbar')
    out = []
    for w in re.findall(r"[a-z0-9]+", s):
        w = SYN.get(w, w)
        if w.endswith('s') and len(w) > 3 and w not in ('press', 'cross', 'glutes', 'abs', 'biceps', 'triceps', 'lats', 'calves'):
            w = SYN.get(w[:-1], w[:-1])
        if w:
            out.append(w)
    return out


def ek_class(equip):
    e = ' '.join(equip or []).lower()
    if 'smith' in e: return 'smith'
    if 'band' in e: return 'band'
    if 'cable' in e: return 'cable'
    if 'machine' in e or 't-bar' in e: return 'machine'
    if 'dumbbell' in e: return 'dumbbell'
    if 'kettlebell' in e: return 'kettlebell'
    if 'barbell' in e: return 'barbell'
    if 'ball' in e or 'bosu' in e or 'balance' in e: return 'ball'
    if 'body' in e or 'bar' in e or 'bench' in e: return 'body'
    return 'other'


def lib_class(eq):
    return {'BARBELL': 'barbell', 'DUMBBELL': 'dumbbell', 'BODY_ONLY': 'body', 'CABLE': 'cable', 'MACHINE': 'machine',
            'KETTLEBELLS': 'kettlebell', 'BANDS': 'band', 'EZ_BAR': 'barbell', 'MEDICINE_BALL': 'ball',
            'EXERCISE_BALL': 'ball'}.get(eq, 'other')


def load_ek():
    out = []
    for f in sorted(os.listdir(EK)):
        for j in os.listdir(f'{EK}/{f}'):
            if j.endswith('.json'):
                d = json.load(open(f'{EK}/{f}/{j}'))
                if os.path.exists(f'{EK}/{f}/{f}-relaxation.png') and os.path.exists(f'{EK}/{f}/{f}-tension.png'):
                    out.append(dict(num=f, slug=d['name'], title=d['title'], cls=ek_class(d.get('equipment'))))
    return out


def score(name, cls, ek):
    a = set(toks(name)); b = set(toks(ek['title']))
    ca = {t for t in a if t not in EQUIP_WORDS}; cb = {t for t in b if t not in EQUIP_WORDS}
    if not ca or not cb: return 0
    j = len(ca & cb) / len(ca | cb)
    va, vb = a & VARIANT, b & VARIANT
    j -= 0.1 * len(va ^ vb)
    if cls != ek['cls'] and not {cls, ek['cls']} <= {'machine', 'smith'}: j -= 0.4
    return j
