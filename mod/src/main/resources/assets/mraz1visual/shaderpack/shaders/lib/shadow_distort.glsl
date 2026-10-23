// Distorted shadow map: more resolution near the player (common technique).
vec3 distortShadow(vec3 p) {
    float d = length(p.xy);
    p.xy /= mix(1.0, d, 0.85) ;
    p.z *= 0.25;
    return p;
}
