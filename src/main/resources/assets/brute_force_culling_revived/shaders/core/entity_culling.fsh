#version 150

uniform vec2 EntityCullingSize;
uniform mat4 CullingViewMat;
uniform vec3 CullingCameraPos;
uniform mat4 CullingProjMat;
uniform vec3 FrustumPos;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

flat in vec3 Pos;
flat in vec2 Size;
flat in vec4 frustum[6];
flat in vec2 DepthScreenSize[5];

out vec4 fragColor;

float near = 0.1;
float far  = 1000.0;
float depthBias = 0.25;

int getSampler(float xLength, float yLength) {
    for (int i = 0; i < 5; ++i) {
        float xStep = 2.0 / DepthScreenSize[i].x;
        float yStep = 2.0 / DepthScreenSize[i].y;
        if (xStep > xLength && yStep > yLength) {
            return i;
        }
    }
    return 4;
}

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (near * far) / (far + near - z * (far - near));
}

float calculateDistance(vec3 P, vec3 Q) {
    vec3 d = Q - P;
    return dot(d, d);
}

vec3 worldToScreenSpace(vec3 pos) {
    vec4 cameraSpace = CullingProjMat * CullingViewMat * vec4(pos, 1.0);
    vec3 ndc = cameraSpace.xyz / cameraSpace.w;
    return (ndc + vec3(1.0)) * 0.5;
}

vec3 moveTowardsCamera(vec3 pos, float distance) {
    vec3 direction = normalize(pos - CullingCameraPos);
    vec3 newPos = pos - direction * distance;
    return newPos;
}

bool cubeInFrustum(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    for (int i = 0; i < 6; ++i) {
        if (!(dot(frustum[i], vec4(minX, minY, minZ, 1.0)) > 0.0) &&
        !(dot(frustum[i], vec4(maxX, minY, minZ, 1.0)) > 0.0) &&
        !(dot(frustum[i], vec4(minX, maxY, minZ, 1.0)) > 0.0) &&
        !(dot(frustum[i], vec4(maxX, maxY, minZ, 1.0)) > 0.0) &&
        !(dot(frustum[i], vec4(minX, minY, maxZ, 1.0)) > 0.0) &&
        !(dot(frustum[i], vec4(maxX, minY, maxZ, 1.0)) > 0.0) &&
        !(dot(frustum[i], vec4(minX, maxY, maxZ, 1.0)) > 0.0) &&
        !(dot(frustum[i], vec4(maxX, maxY, maxZ, 1.0)) > 0.0)) {
            return false;
        }
    }
    return true;
}

bool calculateCube(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    float f = minX - FrustumPos.x;
    float f1 = minY - FrustumPos.y;
    float f2 = minZ - FrustumPos.z;
    float f3 = maxX - FrustumPos.x;
    float f4 = maxY - FrustumPos.y;
    float f5 = maxZ - FrustumPos.z;
    return cubeInFrustum(f, f1, f2, f3, f4, f5);
}

bool isVisible(vec3 vec, float width, float height) {
    float minX = vec.x - width;
    float minY = vec.y - height;
    float minZ = vec.z - width;
    float maxX = vec.x + width;
    float maxY = vec.y + height;
    float maxZ = vec.z + width;
    return calculateCube(minX, minY, minZ, maxX, maxY, maxZ);
}

float unpackDepth(vec2 e) {
    return e.x + e.y / 255.0;
}

float getUVDepth(int idx, vec2 uv) {
    if (idx == 0) return unpackDepth(texture(Sampler0, uv).rg) * 500.0;
    else if (idx == 1) return unpackDepth(texture(Sampler1, uv).rg) * 500.0;
    else if (idx == 2) return unpackDepth(texture(Sampler2, uv).rg) * 500.0;
    else if (idx == 3) return unpackDepth(texture(Sampler3, uv).rg) * 500.0;
    return unpackDepth(texture(Sampler4, uv).rg) * 500.0;
}

void main() {
    float halfWidth = Size.x * 0.5;
    float halfHeight = Size.y * 0.5;

    if (!isVisible(Pos, halfWidth, halfHeight)) {
        fragColor = vec4(0.0, 1.0, 0.0, 1.0);
        return;
    }

    vec3 aabb[8] = vec3[](
    Pos + vec3(-halfWidth, -halfHeight, -halfWidth), Pos + vec3(halfWidth, -halfHeight, -halfWidth),
    Pos + vec3(-halfWidth, halfHeight, -halfWidth), Pos + vec3(halfWidth, halfHeight, -halfWidth),
    Pos + vec3(-halfWidth, -halfHeight, halfWidth), Pos + vec3(halfWidth, -halfHeight, halfWidth),
    Pos + vec3(-halfWidth, halfHeight, halfWidth), Pos + vec3(halfWidth, halfHeight, halfWidth)
    );

    float maxX = -0.1, maxY = -0.1, minX = 1.1, minY = 1.1;

    vec3 cameraUp = vec3(CullingViewMat[0].y, CullingViewMat[1].y, CullingViewMat[2].y);
    vec3 cameraRight = vec3(CullingViewMat[0].x, CullingViewMat[1].x, CullingViewMat[2].x);

    for (int i = 0; i < 8; ++i) {
        vec3 screenPos = worldToScreenSpace(aabb[i]);
        if (screenPos.x < 0.0 || screenPos.x > 1.0 || screenPos.y < 0.0 || screenPos.y > 1.0 || screenPos.z < 0.0 || screenPos.z > 1.0) {
            vec3 vectorDir = normalize(aabb[i] - CullingCameraPos);
            if (dot(vectorDir, cameraRight) < 0.0 && screenPos.x > 0.5) screenPos.x = 0.0;
            if (dot(vectorDir, cameraRight) > 0.0 && screenPos.x < 0.5) screenPos.x = 1.0;
            if (dot(vectorDir, cameraUp) < 0.0 && screenPos.y > 0.5) screenPos.y = 0.0;
            if (dot(vectorDir, cameraUp) > 0.0 && screenPos.y < 0.5) screenPos.y = 1.0;
        }
        minX = min(minX, screenPos.x); maxX = max(maxX, screenPos.x);
        minY = min(minY, screenPos.y); maxY = max(maxY, screenPos.y);
    }

    minX = clamp(minX, 0.0, 1.0); maxX = clamp(maxX, 0.0, 1.0);
    minY = clamp(minY, 0.0, 1.0); maxY = clamp(maxY, 0.0, 1.0);

    int idx = getSampler(maxX - minX, maxY - minY);
    float xStep = 1.0 / DepthScreenSize[idx].x;
    float yStep = 1.0 / DepthScreenSize[idx].y;

    vec3 towardsEntity = normalize(Pos - CullingCameraPos);
    float nearSurfaceOffset = dot(abs(towardsEntity), vec3(halfWidth, halfHeight, halfWidth));
    float entityDepth = LinearizeDepth(worldToScreenSpace(moveTowardsCamera(Pos, nearSurfaceOffset)).z) - depthBias;

    for (float x = minX; x <= maxX + 0.001; x += max((maxX - minX) / 3.0, 0.001)) {
        for (float y = minY; y <= maxY + 0.001; y += max((maxY - minY) / 3.0, 0.001)) {
            if (entityDepth < getUVDepth(idx, vec2(x, y))) {
                fragColor = vec4(0.0, 1.0, 0.0, 1.0);
                return;
            }
        }
    }
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}