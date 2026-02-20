#version 330 core

// Input attributes from VBO — 7 floats per vertex:
//   pos(3)  uv(2)  layer(1)  brightness(1)
layout(location = 0) in vec3  a_Position;    // world-space block corner
layout(location = 1) in vec2  a_TexCoord;    // always [0,0]→[1,1] per face
layout(location = 2) in float a_Layer;       // texture array layer index
layout(location = 3) in float a_Brightness;  // simple directional lighting (0-1)

// Uniforms
uniform mat4 u_Projection;
uniform mat4 u_View;
uniform mat4 u_Model;

// Outputs to fragment shader
out vec2  v_TexCoord;
out float v_Layer;
out float v_Brightness;

void main() {
    gl_Position = u_Projection * u_View * u_Model * vec4(a_Position, 1.0);
    v_TexCoord  = a_TexCoord;
    v_Layer     = a_Layer;
    v_Brightness = a_Brightness;
}
