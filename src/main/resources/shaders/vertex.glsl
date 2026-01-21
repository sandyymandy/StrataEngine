#version 330 core

layout(location = 0) in vec3 position;

void main() {
    // Just pass the position directly to the screen (no camera yet!)
    gl_Position = vec4(position.x, position.y, position.z, 1.0);
}