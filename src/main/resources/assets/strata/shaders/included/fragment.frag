#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;

out vec4 out_Color;

uniform sampler2D u_Texture;

void main() {
    // Sample the texture
    vec4 texColor = texture(u_Texture, v_TexCoord);
    
    // DEBUG: Visualize UV coordinates
    // If you see a gradient from black (bottom-left) to yellow (top-right), UVs are working
    // vec4 debugUV = vec4(v_TexCoord.x, v_TexCoord.y, 0.0, 1.0);
    // out_Color = debugUV;
    
    // DEBUG: Show texture color directly (ignore vertex color)
    // If this shows the texture, then vertex color was the problem
    // out_Color = texColor;

    // NORMAL: Texture * vertex color
    out_Color = texColor * v_Color;

    // DEBUG: If texture is black, show magenta to indicate texture loading issue
    // if (texColor.rgb == vec3(0.0, 0.0, 0.0) && ) {
    //     out_Color = vec4(1.0, 0.0, 1.0, 1.0); // Magenta = black texture
    // }
}
