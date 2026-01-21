#version 330 core

in vec3 pass_color;
out vec4 out_Color;

void main() {
    out_Color = vec4(pass_color, 1.0);
}