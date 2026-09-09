#version 150

uniform sampler2D Sampler0;
uniform float RenderDistance;
uniform vec2 ScreenSize;

flat in vec2 DepthScreenSize;

out vec4 fragColor;

float near = 0.1;
float far  = 1000.0;

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (near * far) / (far + near - z * (far - near));
}

vec2 packDepth(float v) {
    float s = clamp(v, 0.0, 1.0) * 255.0;
    float hi = floor(s);
    return vec2(hi / 255.0, s - hi);
}

float unpackDepth(vec2 e) {
    return e.x + e.y / 255.0;
}

void main() {
    float xStep = 1.0 / ScreenSize.x;
    float yStep = 1.0 / ScreenSize.y;

    float minX = gl_FragCoord.x / DepthScreenSize.x;
    float minY = gl_FragCoord.y / DepthScreenSize.y;
    float maxX = min(gl_FragCoord.x + 1.0, DepthScreenSize.x) / DepthScreenSize.x;
    float maxY = min(gl_FragCoord.y + 1.0, DepthScreenSize.y) / DepthScreenSize.y;

    float depth = 0.0;
    if (RenderDistance > 1.0) {
        for (float x = minX - xStep; x <= maxX + xStep; x += xStep) {
            for (float y = minY - yStep; y <= maxY + yStep; y += yStep) {
                vec2 depthUV = vec2(clamp(x, 0.0, 1.0), clamp(y, 0.0, 1.0));
                depth = max(depth, unpackDepth(texture(Sampler0, depthUV).rg));
            }
        }
        fragColor = vec4(packDepth(depth), 0.0, 1.0);
    } else {
        for (float x = minX - xStep; x <= maxX + xStep; x += xStep) {
            for (float y = minY - yStep; y <= maxY + yStep; y += yStep) {
                vec2 depthUV = vec2(clamp(x, 0.0, 1.0), clamp(y, 0.0, 1.0));
                depth = max(depth, texture(Sampler0, depthUV).r);
            }
        }
        fragColor = vec4(packDepth(LinearizeDepth(depth) / 500.0), 0.0, 1.0);
    }
}
