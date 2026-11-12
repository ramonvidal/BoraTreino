# Exercise illustrations

`python3 tools/exercise_images/build.py <everkinetic clone>/data/src/exercises` regenerates the WebP frames in
`core/ui/src/main/assets/exercises/`, `core/model/.../LibraryExerciseImages.kt` and the mappings in
`docs/exercise-images/` from a local clone of https://github.com/everkinetic/data (needs Pillow; nothing is downloaded).
Starter pictures come from the `images` map in `StarterMachines.kt`; library pictures from `LIBRARY` in `build.py`
(pairs checked by eye). `candidates.py` proposes candidate pairs by name and equipment for review.
