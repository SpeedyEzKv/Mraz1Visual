// Pack options (editable in the Iris shader menu)
#define SHADOW_QUALITY 1 // [0 1 2]
#define SSR_STEPS 16 // [8 16 32]
#define SUN_STRENGTH 1.0 // [0.6 0.8 1.0 1.2 1.4]
#define WATER_ALPHA 0.65 // [0.45 0.55 0.65 0.75 0.85]
#define WAVING_PLANTS
#define COLORED_LIGHT
#define LABPBR

const int shadowMapResolution = 2048; // [1024 2048 3072 4096]
const float shadowDistance = 96.0; // [64.0 96.0 128.0 160.0]
const float sunPathRotation = -25.0;
const bool shadowHardwareFiltering = true;
