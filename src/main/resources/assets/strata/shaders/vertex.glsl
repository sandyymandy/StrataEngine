#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color; // New color input

out vec3 pass_color; // Send color to fragment shader

uniform mat4 transformationMatrix;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main() {
    gl_Position = projectionMatrix * viewMatrix * transformationMatrix * vec4(position, 1.0);
    pass_color = color;
}