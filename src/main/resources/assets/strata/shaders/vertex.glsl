#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec2 texCoord;
layout(location = 2) in vec4 color;

out vec2 pass_texCoord;
out vec4 pass_color;

uniform mat4 u_Projection;
uniform mat4 u_View;
uniform mat4 u_Model;

void main() {
    gl_Position = u_Projection * u_View * u_Model * vec4(position, 1.0);
    pass_texCoord = texCoord;
    pass_color = color;
}