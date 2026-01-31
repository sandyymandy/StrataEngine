(function() {
  
  let codec, format;
  let modelExportAction, modelImportAction, animExportAction, animImportAction, skinExportAction;
  
  Plugin.register('strata_engine_format', {
    title: 'Strata Engine Format',
    author: 'SandyMandy',
    description: 'Format support for Strata Engine models and animations',
    icon: 'icon-format_java',
    version: '3.2.0',
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
      
    // Helper function to convert Blockbench cube to raw cuboid data
    function exportCubeData(cube) {
      // Get texture reference - validates consistency across faces
      const textureRef = getElementTexture(cube);

      const faces = {};

      // Export only the faces that exist and have a texture
      for (let faceName of ['north', 'south', 'east', 'west', 'up', 'down']) {
        const faceData = cube.faces[faceName];
        if (faceData && faceData.texture !== null) {

          // Create the basic face object with UVs
          const faceObj = {
            uv: faceData.uv || [0, 0, 16, 16]
          };

          // Only add rotation if it exists and is not 0
          if (faceData.rotation) {
            faceObj.rotation = faceData.rotation;
          }

          faces[faceName] = faceObj;
        }
      }

      const properties = {
          type: 'blockbench_cuboid',
          texture: textureRef,
          origin: [cube.origin[0], cube.origin[1], cube.origin[2]]
      };

      // Add element rotation only if X, Y, or Z is not zero
      if (cube.rotation && (cube.rotation[0] !== 0 || cube.rotation[1] !== 0 || cube.rotation[2] !== 0)) {
        properties.rotation = [cube.rotation[0], cube.rotation[1], cube.rotation[2]];
      }

      properties.from = [cube.from[0], cube.from[1], cube.from[2]]
      properties.to = [cube.to[0], cube.to[1], cube.to[2]]

      if (cube.inflate) {
          properties.inflate = cube.inflate;
      }

      properties.faces = faces

      return properties;
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

        const properties = {
          type: 'blockbench_mesh',
          texture: textureRef,
          origin: [mesh.origin[0], mesh.origin[1], mesh.origin[2]]
        };

        // Add element rotation only if X, Y, or Z is not zero
        if (mesh.rotation && (mesh.rotation[0] !== 0 || mesh.rotation[1] !== 0 || mesh.rotation[2] !== 0)) {
          properties.rotation = [mesh.rotation[0], mesh.rotation[1], mesh.rotation[2]];
        }

        properties.vertices = verticesCopy
        properties.faces = facesCopy

        return properties;
      }
      
      // Create Blockbench element (Cube or Mesh) from stored data
        function createBlockbenchMesh(meshData, parent, name) {

          // --- Handle Cuboid Import ---
          if (meshData.type === 'blockbench_cuboid') {
            const cube = new Cube({
              name: name || 'imported_cube',
              origin: meshData.origin,
              from: meshData.from,
              to: meshData.to,
              rotation: meshData.rotation || [0, 0, 0],
              inflate: meshData.inflate || 0,
              autouv: 0 // Disable Auto-UV to keep our imported mapping
            });

            // Resolve texture by name
            let textureUuid = null;
            if (meshData.texture) {
              const tex = Texture.all.find(t => t.name === meshData.texture);
              if (tex) {
                textureUuid = tex.uuid;
              }
            }

            // Apply faces
            for (let key in cube.faces) {
              if (meshData.faces && meshData.faces[key]) {
                const savedFace = meshData.faces[key];
                cube.faces[key].extend({
                    uv: savedFace.uv,
                    texture: textureUuid,
                    rotation: savedFace.rotation || 0
                });
              } else {
                // If face was not in the export, clear its texture (hide it)
                cube.faces[key].texture = null;
              }
            }

            cube.init();
            cube.addTo(parent);
            return cube;
          }

          // --- Handle Mesh Import ---

          // Use Blockbench's native mesh data
          const mesh = new Mesh({
            name: name || 'imported_mesh',
            vertices: {},
            faces: {},
            // FIX: Pass rotation to Mesh constructor
            rotation: meshData.rotation || [0, 0, 0]
          });

          // Set origin if available
          if (meshData.origin) {
            mesh.origin = meshData.origin;
          }

          // Copy vertices
          if (meshData.vertices) {
            for (let key in meshData.vertices) {
              mesh.vertices[key] = meshData.vertices[key];
            }
          }

          // Recreate faces
          if (meshData.faces) {
            for (let faceKey in meshData.faces) {
              const faceData = meshData.faces[faceKey];
              const face = new MeshFace(mesh, faceData);

              // Resolve texture for mesh face
              if (meshData.texture) {
                 const tex = Texture.all.find(t => t.name === meshData.texture);
                 if (tex) {
                   face.texture = tex.uuid;
                 }
              }
              mesh.faces[faceKey] = face;
            }
          }

          mesh.init();
          mesh.addTo(parent);
          return mesh;
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
                const meshData = exportCubeData(child);
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
                    createBlockbenchMesh(meshData, group, meshName);
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