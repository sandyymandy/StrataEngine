#version 330 core

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;

uniform mat4 u_Projection;
uniform mat4 u_View;

out vec2 v_TexCoord;
out vec4 v_Color;

void main() {
    gl_Position = u_Projection * u_View * vec4(a_Position, 1.0);
    v_TexCoord = a_TexCoord;
    v_Color = a_Color;
}