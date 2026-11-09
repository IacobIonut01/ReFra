// Host helper for generating and checking codescan test fixtures (not shipped).
#include "MultiFormatWriter.h"
#include "BitMatrix.h"
#include "ReadBarcode.h"
#include "CharacterSet.h"
#include <cstdio>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
using namespace ZXing;

static std::string readAll(const char* path) { std::ifstream f(path, std::ios::binary); std::stringstream s; s << f.rdbuf(); return s.str(); }

int main(int argc, char** argv) {
    if (argc >= 6 && std::string(argv[1]) == "encode") {
        auto format = BarcodeFormatFromString(argv[2]);
        std::string text = readAll(argv[3]);
        int size = std::stoi(argv[4]);
        auto bm = MultiFormatWriter(format).setEncoding(CharacterSet::UTF8).setMargin(4).encode(text, size, format == BarcodeFormat::EAN13 ? size / 2 : size);
        auto m = ToMatrix<uint8_t>(bm);
        FILE* f = fopen(argv[5], "wb");
        fprintf(f, "P5\n%d %d\n255\n", m.width(), m.height());
        fwrite(m.data(), 1, m.size(), f);
        fclose(f);
        return 0;
    }
    if (argc >= 3 && std::string(argv[1]) == "decode") {
        std::string data = readAll(argv[2]);
        int w, h, maxv; int pos = 0; char magic[3] = {0};
        std::istringstream hdr(data);
        hdr >> magic[0] >> magic[1] >> w >> h >> maxv; hdr.get();
        pos = (int)hdr.tellg();
        ImageView iv(reinterpret_cast<const uint8_t*>(data.data() + pos), w, h, ImageFormat::Lum);
        ReaderOptions o; o.setTryHarder(argc > 3); o.setTryRotate(argc > 3); o.setTryInvert(argc > 3); o.setTextMode(TextMode::Plain);
        for (auto& b : ReadBarcodes(iv, o)) {
            auto p = b.position();
            std::cout << ToString(b.format()) << "\t" << p.topLeft().x << "," << p.topLeft().y << "\t" << b.text() << "\n";
        }
        return 0;
    }
    std::cerr << "usage: zxtool encode FORMAT textfile size out.pgm | decode in.pgm [hard]\n";
    return 2;
}
