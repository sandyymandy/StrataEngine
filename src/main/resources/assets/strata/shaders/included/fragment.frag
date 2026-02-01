#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 out_Color;

uniform sampler2D u_Texture;

void main() {
    // Sample the texture
    vec4 texColor = texture(u_Texture, v_TexCoord);

    // Multiply by vertex color (tint)
    // Vertex color is (1,1,1,1) for untinted rendering
    out_Color = texColor * v_Color;
}
