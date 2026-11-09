# Code scanner test fixtures

`app/androidApp/src/androidTest/assets/codescan/` holds synthetic photos for `CodeScanNativeTest`:
QR codes, a Data Matrix and an EAN-13 drawn on generated noise backgrounds. They contain no
personal media.

To regenerate them:

1. Build a host copy of the pinned zxing-cpp (the version in `scripts/native/build-zxing.sh`)
   with the writer enabled, then this helper:

   ```bash
   cmake -S <zxing-cpp> -B host-build -G Ninja -DCMAKE_BUILD_TYPE=Release \
       -DBUILD_SHARED_LIBS=OFF -DZXING_WRITERS=OLD -DZXING_EXAMPLES=OFF -DZXING_DEPENDENCIES=LOCAL
   cmake --build host-build --target ZXing
   clang++ -std=c++20 -O2 -I <zxing-cpp>/core/src -I host-build/core zxtool.cpp \
       host-build/core/libZXing.a -o zxtool
   ```

2. Run `python make_fixtures.py ./zxtool <out-dir>` (needs Pillow and NumPy) and copy the JPEGs
   into the assets folder. `expected.json` lists what each image contains.

`zxtool decode <file.pgm> [hard]` reads a grey PGM with the same reader options the app uses for
the quick and thorough passes, which is how the fixtures were checked.
