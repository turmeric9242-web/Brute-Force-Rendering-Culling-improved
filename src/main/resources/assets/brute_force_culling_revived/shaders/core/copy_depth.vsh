#version 150

in vec3 Position;

uniform float DepthSize[10];

flat out vec2 DepthScreenSize;

void main() {
    DepthScreenSize = vec2(DepthSize[0], DepthSize[1]);

    gl_Position = vec4(Position, 1.0);
}
