(function() {
  
  let codec, format;
  let modelExportAction, modelImportAction, animExportAction, animImportAction, skinExportAction;
  
  Plugin.register('strata_engine_format', {
    title: 'Strata Engine Format',
    author: 'SandyMandy',
    description: 'Format support for Strata Engine models and animations',
    icon: 'icon-format_java',
    version: '3.1.0',
    variant: 'both',
    
    onload() {
      
      // Helper function to get texture name from a cube face
      function getTextureNameFromFace(face) {
        if (face && face.texture !== null) {
          const texture = Texture.all.find(t => t.uuid === face.texture);
          if (texture) {
            return texture.name;
          }
        }
        return null;
      }
      
      // Helper function to get the element's texture and validate consistency
      function getElementTexture(cube) {
        let textureRef = "main";
        let foundTexture = null;
        const textures = new Set();
        
        if (cube.faces) {
          for (let faceName of ['north', 'south', 'east', 'west', 'up', 'down']) {
            const face = cube.faces[faceName];
            const texName = getTextureNameFromFace(face);
            if (texName) {
              textures.add(texName);
              if (!foundTexture) {
                foundTexture = texName;
              }
            }
          }
        }
        
        // Warn if multiple textures on one element
        if (textures.size > 1) {
          console.warn(`Element "${cube.name || 'unnamed'}" has multiple textures: ${Array.from(textures).join(', ')}. Using first found: ${foundTexture}`);
          Blockbench.showMessageBox({
            title: 'Multiple Textures Warning',
            message: `Element "${cube.name || 'unnamed'}" has multiple textures applied to different faces. Strata Engine only supports one texture per element.\n\nUsing: ${foundTexture}\n\nPlease ensure each element uses only one texture.`,
            icon: 'warning'
          });
        }
        
        return foundTexture || textureRef;
      }
      
      // Helper function to convert Blockbench cube to mesh data
      function convertCubeToMesh(cube) {
        const vertices = [];
        const indices = [];
        
        // Get texture reference - now with validation
        const textureRef = getElementTexture(cube);
        
        // Define the 8 corners of the cube
        const from = cube.from;
        const to = cube.to;
        
        const corners = [
          [from[0], from[1], from[2]], // 0
          [to[0],   from[1], from[2]], // 1
          [to[0],   to[1],   from[2]], // 2
          [from[0], to[1],   from[2]], // 3
          [from[0], from[1], to[2]],   // 4
          [to[0],   from[1], to[2]],   // 5
          [to[0],   to[1],   to[2]],   // 6
          [from[0], to[1],   to[2]]    // 7
        ];
        
        // Define faces with their vertices and normals
        const faces = [
          { name: 'north', verts: [0, 1, 2, 3], normal: [0, 0, -1] },
          { name: 'south', verts: [5, 4, 7, 6], normal: [0, 0, 1] },
          { name: 'west',  verts: [4, 0, 3, 7], normal: [-1, 0, 0] },
          { name: 'east',  verts: [1, 5, 6, 2], normal: [1, 0, 0] },
          { name: 'down',  verts: [4, 5, 1, 0], normal: [0, -1, 0] },
          { name: 'up',    verts: [3, 2, 6, 7], normal: [0, 1, 0] }
        ];
        
        let vertexIndex = 0;
        
        faces.forEach(face => {
          const faceData = cube.faces[face.name];
          let uv = [0, 0, 16, 16];
          
          if (faceData && faceData.uv) {
            uv = faceData.uv;
          }
          
          // Calculate UV coordinates
          const uvCoords = [
            [uv[0] / 64, uv[1] / 64],
            [uv[2] / 64, uv[1] / 64],
            [uv[2] / 64, uv[3] / 64],
            [uv[0] / 64, uv[3] / 64]
          ];
          
          // Add vertices for this face
          face.verts.forEach((cornerIdx, i) => {
            vertices.push({
              pos: corners[cornerIdx],
              uv: uvCoords[i],
              normal: face.normal
            });
          });
          
          // Add indices for two triangles
          const baseIdx = vertexIndex;
          indices.push(baseIdx, baseIdx + 1, baseIdx + 2);
          indices.push(baseIdx, baseIdx + 2, baseIdx + 3);
          
          vertexIndex += 4;
        });
        
        return {
          texture: textureRef,
          origin: [cube.origin[0], cube.origin[1], cube.origin[2]],
          vertices: vertices,
          indices: indices
        };
      }
      
      // Store mesh in Blockbench's native format
      function exportMeshData(mesh) {
        // Get texture reference from the mesh's faces - validate consistency
        let textureRef = "main";
        const textures = new Set();
        
        // Check all faces for textures
        if (mesh.faces) {
          for (let faceKey in mesh.faces) {
            const face = mesh.faces[faceKey];
            if (face.texture !== null && face.texture !== undefined) {
              const texture = Texture.all.find(t => t.uuid === face.texture);
              if (texture) {
                textures.add(texture.name);
                if (textureRef === "main") {
                  textureRef = texture.name;
                }
              }
            }
          }
        }
        
        // Warn if multiple textures on one mesh
        if (textures.size > 1) {
          console.warn(`Mesh has multiple textures: ${Array.from(textures).join(', ')}. Using first found: ${textureRef}`);
          Blockbench.showMessageBox({
            title: 'Multiple Textures Warning',
            message: `A mesh element has multiple textures applied to different faces. Strata Engine only supports one texture per element.\n\nUsing: ${textureRef}\n\nPlease ensure each mesh uses only one texture.`,
            icon: 'warning'
          });
        }
        
        // Deep clone to avoid references
        const verticesCopy = {};
        const facesCopy = {};
        
        // Copy vertices
        for (let key in mesh.vertices) {
          verticesCopy[key] = [...mesh.vertices[key]];
        }
        
        // Copy faces (without texture field since we use per-mesh texture)
        for (let faceKey in mesh.faces) {
          const face = mesh.faces[faceKey];
          facesCopy[faceKey] = {
            vertices: [...face.vertices],
            uv: {}
          };
          
          // Copy UV data
          if (face.uv) {
            for (let vKey in face.uv) {
              facesCopy[faceKey].uv[vKey] = [...face.uv[vKey]];
            }
          }
        }
        
        return {
          type: 'blockbench_mesh',
          texture: textureRef,
          origin: [mesh.origin[0], mesh.origin[1], mesh.origin[2]],
          vertices: verticesCopy,
          faces: facesCopy
        };
      }
      
      // Create Blockbench mesh from stored data
      function createBlockbenchMesh(meshData, parent) {
        // If it's a cube-based mesh (old format), convert it
        if (meshData.type !== 'blockbench_mesh' && meshData.vertices && Array.isArray(meshData.vertices)) {
          return createBlockbenchMeshFromLegacy(meshData, parent);
        }
        
        // Use Blockbench's native mesh data
        const mesh = new Mesh({
          name: 'imported_mesh',
          vertices: {},
          faces: {}
        });
        
        // Set origin if available
        if (meshData.origin) {
          mesh.origin = meshData.origin;
        }
        
        // Copy vertices first
        if (meshData.vertices) {
          for (let key in meshData.vertices) {
            mesh.vertices[key] = meshData.vertices[key];
          }
        }
        
        // Then recreate faces
        if (meshData.faces) {
          for (let faceKey in meshData.faces) {
            const faceData = meshData.faces[faceKey];
            const face = new MeshFace(mesh, faceData);
            mesh.faces[faceKey] = face;
          }
        }
        
        mesh.init();
        mesh.addTo(parent);
        return mesh;
      }
      
      // Fallback for legacy format
      function createBlockbenchMeshFromLegacy(meshData, parent) {
        if (meshData.vertices && meshData.vertices.length > 0) {
          const mesh = new Mesh({
            name: 'imported_mesh',
            vertices: {},
            faces: {}
          });
          
          // Set origin if available
          if (meshData.origin) {
            mesh.origin = meshData.origin;
          }
          
          const indexToKeyMap = [];
          
          meshData.vertices.forEach(vertexData => {
            const keys = mesh.addVertices(vertexData.pos);
            indexToKeyMap.push(keys[0]);
          });
          
          if (meshData.indices) {
            for (let i = 0; i < meshData.indices.length; i += 3) {
              const idx0 = meshData.indices[i];
              const idx1 = meshData.indices[i + 1];
              const idx2 = meshData.indices[i + 2];
              
              const key0 = indexToKeyMap[idx0];
              const key1 = indexToKeyMap[idx1];
              const key2 = indexToKeyMap[idx2];
              
              if (key0 && key1 && key2) {
                const face = new MeshFace(mesh, {
                  vertices: [key0, key1, key2]
                });
                
                const v0 = meshData.vertices[idx0];
                const v1 = meshData.vertices[idx1];
                const v2 = meshData.vertices[idx2];
                
                if (v0.uv && v1.uv && v2.uv) {
                  face.uv = {};
                  face.uv[key0] = [v0.uv[0] * Project.texture_width, v0.uv[1] * Project.texture_height];
                  face.uv[key1] = [v1.uv[0] * Project.texture_width, v1.uv[1] * Project.texture_height];
                  face.uv[key2] = [v2.uv[0] * Project.texture_width, v2.uv[1] * Project.texture_height];
                }
                
                mesh.addFaces(face);
              }
            }
          }
          
          mesh.init();
          mesh.addTo(parent);
          return mesh;
        }
      }
      
      // Prompt for model ID
      function promptForModelId(defaultNamespace = 'strata', defaultName = 'model') {
        return new Promise((resolve) => {
          const dialog = new Dialog({
            id: 'strata_model_id',
            title: 'Model ID',
            form: {
              namespace: {
                label: 'Namespace',
                type: 'text',
                value: defaultNamespace
              },
              name: {
                label: 'Model Name',
                type: 'text',
                value: defaultName
              }
            },
            onConfirm: function(formData) {
              const namespace = formData.namespace || 'strata';
              const name = formData.name || 'model';
              resolve(`${namespace}:${name}`);
              dialog.hide();
            },
            onCancel: function() {
              resolve(null);
              dialog.hide();
            }
          });
          dialog.show();
        });
      }
      
      // Define the Strata codec
      codec = new Codec('strata_model', {
        name: 'Strata Model',
        extension: 'strmodel',
        remember: true,
        
        compile(options) {
          const bones = [];
          const meshes = {};
          const usedTextures = new Set();
          
          function processBone(group, parentName = null) {
            const bone = {
              name: group.name,
              parent: parentName,
              pivot: [group.origin[0], group.origin[1], group.origin[2]],
              rotation: [group.rotation[0], group.rotation[1], group.rotation[2]],
              meshes: []
            };
            
            group.children.forEach(child => {
              if (child instanceof Cube) {
                const meshName = `${group.name}_mesh_${Object.keys(meshes).length}`;
                bone.meshes.push(meshName);
                const meshData = convertCubeToMesh(child);
                meshes[meshName] = meshData;
                
                // Track the actual texture used by this specific mesh
                usedTextures.add(meshData.texture);
              } else if (child instanceof Mesh) {
                const meshName = `${group.name}_mesh_${Object.keys(meshes).length}`;
                bone.meshes.push(meshName);
                
                const meshData = exportMeshData(child);
                meshes[meshName] = meshData;
                
                // Track the actual texture used by this specific mesh
                usedTextures.add(meshData.texture);
              }
            });
            
            bones.push(bone);
            
            group.children.forEach(child => {
              if (child instanceof Group) {
                processBone(child, group.name);
              }
            });
          }
          
          Outliner.root.forEach(element => {
            if (element instanceof Group) {
              processBone(element, null);
            }
          });
          
          // Convert set to array for texture names only
          const textureArray = Array.from(usedTextures);
          
          if (textureArray.length === 0) {
            textureArray.push("main");
          }
          
          return {
            id: options.modelId || 'strata:model',
            format_version: 3,
            textures: textureArray,
            bones: bones,
            meshes: meshes
          };
        },
        
        parse(model, path) {
          this.dispatchEvent('parse', {model});
          
          Project.texture_width = 64;
          Project.texture_height = 64;
          
          // Don't create textures on import - let the user handle that
          
          const boneGroups = {};
          
          if (model.bones) {
            model.bones.forEach(bone => {
              const group = new Group({
                name: bone.name,
                origin: bone.pivot,
                rotation: bone.rotation
              }).init();
              
              boneGroups[bone.name] = group;
              
              if (bone.meshes && model.meshes) {
                bone.meshes.forEach(meshName => {
                  const meshData = model.meshes[meshName];
                  if (meshData) {
                    createBlockbenchMesh(meshData, group);
                  }
                });
              }
            });
            
            model.bones.forEach(bone => {
              if (bone.parent && boneGroups[bone.parent]) {
                boneGroups[bone.name].addTo(boneGroups[bone.parent]);
              }
            });
          }
          
          this.dispatchEvent('parsed', {model});
        },
        
        compile_animation(animation, modelId) {
          const animData = {
            id: modelId,
            format_version: 3,
            length: animation.length,
            loop: animation.loop === 'loop',
            bones: {},
            events: {}
          };
          
          for (let uuid in animation.animators) {
            const animator = animation.animators[uuid];
            const bone = animator.group;
            
            if (!bone) continue;
            
            const boneData = {};
            
            if (animator.rotation && animator.rotation.length > 0) {
              boneData.rotation = [];
              animator.rotation.forEach(kf => {
                const keyframe = {};
                keyframe[kf.time.toString()] = {
                  vector: [kf.data_points[0].x, kf.data_points[0].y, kf.data_points[0].z]
                };
                boneData.rotation.push(keyframe);
              });
            }
            
            if (animator.position && animator.position.length > 0) {
              boneData.position = [];
              animator.position.forEach(kf => {
                const keyframe = {};
                keyframe[kf.time.toString()] = {
                  vector: [kf.data_points[0].x, kf.data_points[0].y, kf.data_points[0].z]
                };
                boneData.position.push(keyframe);
              });
            }
            
            if (animator.scale && animator.scale.length > 0) {
              boneData.scale = [];
              animator.scale.forEach(kf => {
                const keyframe = {};
                keyframe[kf.time.toString()] = {
                  vector: [kf.data_points[0].x, kf.data_points[0].y, kf.data_points[0].z]
                };
                boneData.scale.push(keyframe);
              });
            }
            
            if (Object.keys(boneData).length > 0) {
              animData.bones[bone.name] = boneData;
            }
          }
          
          if (animation.markers && animation.markers.length > 0) {
            animation.markers.forEach(marker => {
              animData.events[marker.time.toString()] = marker.name;
            });
          }
          
          return animData;
        },
        
        parse_animation(data, path, name) {
          const animation = new Animation({
            name: name,
            length: data.length || 1,
            loop: data.loop ? 'loop' : 'once'
          }).add();
          
          if (data.bones) {
            Object.entries(data.bones).forEach(([boneName, tracks]) => {
              const group = Group.all.find(g => g.name === boneName);
              if (!group) return;
              
              const animator = animation.getBoneAnimator(group);
              
              if (tracks.rotation) {
                tracks.rotation.forEach(kfObj => {
                  Object.entries(kfObj).forEach(([time, data]) => {
                    animator.addKeyframe({
                      channel: 'rotation',
                      time: parseFloat(time),
                      data_points: [{
                        x: data.vector[0],
                        y: data.vector[1],
                        z: data.vector[2]
                      }]
                    });
                  });
                });
              }
              
              if (tracks.position) {
                tracks.position.forEach(kfObj => {
                  Object.entries(kfObj).forEach(([time, data]) => {
                    animator.addKeyframe({
                      channel: 'position',
                      time: parseFloat(time),
                      data_points: [{
                        x: data.vector[0],
                        y: data.vector[1],
                        z: data.vector[2]
                      }]
                    });
                  });
                });
              }
              
              if (tracks.scale) {
                tracks.scale.forEach(kfObj => {
                  Object.entries(kfObj).forEach(([time, data]) => {
                    animator.addKeyframe({
                      channel: 'scale',
                      time: parseFloat(time),
                      data_points: [{
                        x: data.vector[0],
                        y: data.vector[1],
                        z: data.vector[2]
                      }]
                    });
                  });
                });
              }
            });
          }
          
          if (data.events) {
            Object.entries(data.events).forEach(([time, eventName]) => {
              animation.markers.push({
                time: parseFloat(time),
                name: eventName
              });
            });
          }
          
          return animation;
        }
      });
      
      // Create the Strata format
      format = new ModelFormat({
        id: 'strata_model_format',
        name: 'Strata Model',
        description: 'Model and animation format for Strata Engine',
        icon: 'icon-format_java',
        category: 'minecraft',
        target: ['Modded Entity'],
        format_page: {
          content: [
            '## Strata Engine Format',
            '',
            'This format supports:',
            '- Model ID system (namespace:name)',
            '- Hierarchical bone structure',
            '- Mesh-based geometry',
            '- Separate skin files (.strskin) for texture paths',
            '- Rotation, position, and scale animations',
            '- Animation events via timeline markers'
          ]
        },
        codec,
        display_mode: false,
        meshes: true,
        locators: false,
        rotation_limit: false,
        uv_rotation: true,
        java_face_properties: false,
        select_texture_for_particles: false,
        bone_rig: true,
        centered_grid: true,
        rotate_cubes: true,
        stretch_cubes: true,
        animated_textures: true,
        animation_files: true,
        animation_mode: true,
        
        animation_file_format: {
          extension: 'stranim',
          remember: true,
          
          compile(options) {
            const animations = {};
            
            Animation.all.forEach(anim => {
              animations[anim.name] = codec.compile_animation(anim, options.modelId);
            });
            
            return animations;
          },
          
          parse(data, path) {
            if (typeof data === 'object' && !Array.isArray(data)) {
              Object.entries(data).forEach(([name, animData]) => {
                codec.parse_animation(animData, path, name);
              });
            }
          }
        }
      });
      
      // Model Export Action
      modelExportAction = new Action('export_strata_model', {
        name: 'Export Strata Model (.strmodel)',
        icon: 'save',
        description: 'Export model to Strata Engine format',
        category: 'file',
        condition: () => Format === format,
        async click() {
          const modelId = await promptForModelId('strata', Project.name || 'model');
          if (!modelId) return;
          
          const modelData = codec.compile({ modelId });
          
          Blockbench.export({
            type: 'Strata Model',
            extensions: ['strmodel'],
            name: Project.name || 'model',
            content: JSON.stringify(modelData, null, 2),
            savetype: 'text'
          }, (path) => {
            // After saving model, also save skin file
            if (path) {
              const skinData = {
                id: modelId,
                textures: {}
              };
              
              // Build texture paths from texture names
              modelData.textures.forEach(texName => {
                skinData.textures[texName] = `strata:entity/${texName.toLowerCase()}`;
              });
              
              const skinPath = path.replace('.strmodel', '.strskin');
              const fs = require('fs');
              fs.writeFileSync(skinPath, JSON.stringify(skinData, null, 2));
              
              Blockbench.showQuickMessage(`Exported model and skin files`);
            }
          });
        }
      });
      
      // Model Import Action
      modelImportAction = new Action('import_strata_model', {
        name: 'Import Strata Model (.strmodel)',
        icon: 'folder_open',
        description: 'Import model from Strata Engine format',
        category: 'file',
        click() {
          Blockbench.import({
            extensions: ['strmodel'],
            type: 'Strata Model',
            readtype: 'text'
          }, (files) => {
            files.forEach(file => {
              try {
                const model = autoParseJSON(file.content);
                
                if (!model) {
                  Blockbench.showMessageBox({
                    title: 'Import Error',
                    message: 'Invalid JSON in model file'
                  });
                  return;
                }
                
                newProject(format);
                codec.parse(model, file.path);
                Canvas.updateAll();
                
              } catch (error) {
                Blockbench.showMessageBox({
                  title: 'Import Error',
                  message: 'Failed to import model: ' + error.message
                });
              }
            });
          });
        }
      });
      
      // Animation Export Action
      animExportAction = new Action('export_strata_animations', {
        name: 'Export Strata Animations',
        icon: 'movie',
        description: 'Export all animations to .stranim file',
        category: 'file',
        condition: () => Format === format && Animation.all.length > 0,
        async click() {
          const modelId = await promptForModelId('strata', Project.name || 'model');
          if (!modelId) return;
          
          const animations = {};
          
          Animation.all.forEach(anim => {
            animations[anim.name] = codec.compile_animation(anim, modelId);
          });
          
          Blockbench.export({
            type: 'Strata Animation',
            extensions: ['stranim'],
            name: (Project.name || 'animation'),
            content: JSON.stringify(animations, null, 2)
          });
        }
      });
      
      // Animation Import Action
      animImportAction = new Action('import_strata_animations', {
        name: 'Import Strata Animations',
        icon: 'movie',
        description: 'Import animations from .stranim file',
        category: 'file',
        condition: () => Format === format,
        click() {
          Blockbench.import({
            extensions: ['stranim'],
            type: 'Strata Animation',
            readtype: 'text'
          }, (files) => {
            files.forEach(file => {
              try {
                const animData = autoParseJSON(file.content);
                
                if (animData) {
                  Object.entries(animData).forEach(([name, data]) => {
                    codec.parse_animation(data, file.path, name);
                  });
                  
                  Blockbench.showQuickMessage('Animations imported successfully');
                }
              } catch (error) {
                Blockbench.showMessageBox({
                  title: 'Import Error',
                  message: 'Failed to import animations: ' + error.message
                });
              }
            });
          });
        }
      });
      
      MenuBar.addAction(modelExportAction, 'file.export');
      MenuBar.addAction(modelImportAction, 'file.import');
      MenuBar.addAction(animExportAction, 'file.export');
      MenuBar.addAction(animImportAction, 'file.import');
      
      console.log('Strata Engine Format plugin loaded successfully');
    },
    
    onunload() {
      codec.delete();
      format.delete();
      modelExportAction.delete();
      modelImportAction.delete();
      animExportAction.delete();
      animImportAction.delete();
    }
  });
  
})();