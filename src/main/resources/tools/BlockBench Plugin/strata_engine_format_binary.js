(function() {
  const STRATA_FORMAT_VERSION = 2.0; // Binary format version

  let codec, format;
  let modelExportAction, modelImportAction, animExportAction, animImportAction;

  const _Cube = typeof ElementCube !== 'undefined' ? ElementCube : Cube;
  const _Mesh = typeof ElementMesh !== 'undefined' ? ElementMesh : Mesh;
  const _MeshFace = typeof ElementMeshFace !== 'undefined' ? ElementMeshFace : MeshFace;
  const _isBB5 = Blockbench.isNewerThan('4.99');

  // ============================================================================
  // BINARY WRITER - Builds binary data from typed arrays
  // ============================================================================
  class BinaryWriter {
    constructor() {
      this.buffers = [];
      this.length = 0;
    }

    // Write unsigned 8-bit integer (0-255)
    writeUInt8(value) {
      const buffer = new Uint8Array(1);
      buffer[0] = value;
      this.buffers.push(buffer);
      this.length += 1;
    }

    // Write signed 16-bit integer (-32768 to 32767)
    writeInt16(value) {
      const buffer = new ArrayBuffer(2);
      new DataView(buffer).setInt16(0, value, true); // true = little-endian
      this.buffers.push(new Uint8Array(buffer));
      this.length += 2;
    }

    // Write unsigned 16-bit integer (0-65535)
    writeUInt16(value) {
      const buffer = new ArrayBuffer(2);
      new DataView(buffer).setUint16(0, value, true);
      this.buffers.push(new Uint8Array(buffer));
      this.length += 2;
    }

    // Write unsigned 32-bit integer (0-4294967295)
    writeUInt32(value) {
      const buffer = new ArrayBuffer(4);
      new DataView(buffer).setUint32(0, value, true);
      this.buffers.push(new Uint8Array(buffer));
      this.length += 4;
    }

    // Write 32-bit floating point number
    writeFloat32(value) {
      const buffer = new ArrayBuffer(4);
      new DataView(buffer).setFloat32(0, value, true);
      this.buffers.push(new Uint8Array(buffer));
      this.length += 4;
    }

    // Write array of 3 floats (vector3)
    writeVector3(vec) {
      this.writeFloat32(vec[0]);
      this.writeFloat32(vec[1]);
      this.writeFloat32(vec[2]);
    }

    // Write UTF-8 string with length prefix
    writeString(str) {
      const encoder = new TextEncoder();
      const bytes = encoder.encode(str);
      this.writeUInt16(bytes.length);
      this.buffers.push(bytes);
      this.length += bytes.length;
    }

    // Write fixed ASCII string (for magic number)
    writeFixedString(str, length) {
      const encoder = new TextEncoder();
      const bytes = encoder.encode(str);
      const buffer = new Uint8Array(length);
      buffer.set(bytes.slice(0, length));
      this.buffers.push(buffer);
      this.length += length;
    }

    // Combine all buffers into final Uint8Array
    toBuffer() {
      const result = new Uint8Array(this.length);
      let offset = 0;
      for (const buffer of this.buffers) {
        result.set(buffer, offset);
        offset += buffer.length;
      }
      return result;
    }
  }

  // ============================================================================
  // BINARY READER - Reads binary data with proper offset tracking
  // ============================================================================
  class BinaryReader {
    constructor(buffer) {
      this.buffer = buffer;
      this.view = new DataView(buffer.buffer, buffer.byteOffset, buffer.byteLength);
      this.offset = 0;
    }

    readUInt8() {
      const value = this.buffer[this.offset];
      this.offset += 1;
      return value;
    }

    readInt16() {
      const value = this.view.getInt16(this.offset, true);
      this.offset += 2;
      return value;
    }

    readUInt16() {
      const value = this.view.getUint16(this.offset, true);
      this.offset += 2;
      return value;
    }

    readUInt32() {
      const value = this.view.getUint32(this.offset, true);
      this.offset += 4;
      return value;
    }

    readFloat32() {
      const value = this.view.getFloat32(this.offset, true);
      this.offset += 4;
      return value;
    }

    readVector3() {
      return [
        this.readFloat32(),
        this.readFloat32(),
        this.readFloat32()
      ];
    }

    readString() {
      const length = this.readUInt16();
      const bytes = this.buffer.slice(this.offset, this.offset + length);
      this.offset += length;
      const decoder = new TextDecoder();
      return decoder.decode(bytes);
    }

    readFixedString(length) {
      const bytes = this.buffer.slice(this.offset, this.offset + length);
      this.offset += length;
      const decoder = new TextDecoder();
      // Find null terminator
      let end = 0;
      while (end < bytes.length && bytes[end] !== 0) end++;
      return decoder.decode(bytes.slice(0, end));
    }
  }

  // ============================================================================
  // HELPER FUNCTIONS (from original format)
  // ============================================================================

  // Calculate bounding box for the entire model
  function calculateModelBoundingBox() {
    let minX = Infinity, minY = Infinity, minZ = Infinity;
    let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;

    // Process all cubes
    for (const cube of Cube.all) {
      if (!cube.visibility) continue;

      const [x1, y1, z1] = cube.from;
      const [x2, y2, z2] = cube.to;
      const inflate = cube.inflate || 0;

      const corners = [
        [x1 - inflate, y1 - inflate, z1 - inflate],
        [x2 + inflate, y1 - inflate, z1 - inflate],
        [x1 - inflate, y2 + inflate, z1 - inflate],
        [x2 + inflate, y2 + inflate, z1 - inflate],
        [x1 - inflate, y1 - inflate, z2 + inflate],
        [x2 + inflate, y1 - inflate, z2 + inflate],
        [x1 - inflate, y2 + inflate, z2 + inflate],
        [x2 + inflate, y2 + inflate, z2 + inflate]
      ];

      for (const corner of corners) {
        minX = Math.min(minX, corner[0]);
        minY = Math.min(minY, corner[1]);
        minZ = Math.min(minZ, corner[2]);
        maxX = Math.max(maxX, corner[0]);
        maxY = Math.max(maxY, corner[1]);
        maxZ = Math.max(maxZ, corner[2]);
      }
    }

    // Process all meshes
    for (const mesh of Mesh.all) {
      if (!mesh.visibility) continue;

      const meshOrigin = mesh.origin || [0, 0, 0];

      for (const key in mesh.vertices) {
        const vertex = mesh.vertices[key];
        const worldVertex = [
          vertex[0] + meshOrigin[0],
          vertex[1] + meshOrigin[1],
          vertex[2] + meshOrigin[2]
        ];

        minX = Math.min(minX, worldVertex[0]);
        minY = Math.min(minY, worldVertex[1]);
        minZ = Math.min(minZ, worldVertex[2]);
        maxX = Math.max(maxX, worldVertex[0]);
        maxY = Math.max(maxY, worldVertex[1]);
        maxZ = Math.max(maxZ, worldVertex[2]);
      }
    }

    if (minX === Infinity) {
      return {
        min: [0, 0, 0],
        max: [0, 0, 0]
      };
    }

    return {
      min: [minX, minY, minZ],
      max: [maxX, maxY, maxZ]
    };
  }

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

  // Helper function to get the element's texture
  function getElementTexture(cube) {
    let textureRef = "untextured";
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

    if (textures.size > 1) {
      console.warn(`Element "${cube.name || 'unnamed'}" has multiple textures: ${Array.from(textures).join(', ')}. Using first found: ${foundTexture}`);
    }

    return foundTexture || textureRef;
  }

  // Create Blockbench element from parsed data
  function createBlockbenchElement(meshData, parent, meshKey, textureMap) {
    if (meshData.type === 'blockbench_cuboid') {
      const cube = new _Cube({
        name: meshData.name || meshKey || 'imported_cube',
        origin: meshData.origin,
        from: meshData.from,
        to: meshData.to,
        rotation: meshData.rotation || [0, 0, 0],
        inflate: meshData.inflate || 0,
        autouv: 0
      });

      if (meshData.hidden) {
        cube.visibility = false;
      }

      let textureUuid = null;
      if (meshData.texture && textureMap.has(meshData.texture)) {
        textureUuid = textureMap.get(meshData.texture).uuid;
      }

      for (let key in cube.faces) {
        if (meshData.faces && meshData.faces[key]) {
          const savedFace = meshData.faces[key];
          cube.faces[key].extend({
            uv: savedFace.uv,
            texture: textureUuid,
            rotation: savedFace.rotation || 0
          });
        } else {
          cube.faces[key].texture = null;
        }
      }

      cube.init();
      cube.addTo(parent);
      return cube;
    }

    // Mesh
    const mesh = new _Mesh({
      name: meshData.name || meshKey || 'imported_mesh',
      vertices: {},
      faces: {},
      rotation: meshData.rotation || [0, 0, 0]
    });

    if (_isBB5 && meshData.shade_smooth) {
      mesh.shading = "smooth";
    }

    if (meshData.hidden) {
      mesh.visibility = false;
    }

    if (meshData.origin) {
      mesh.origin = meshData.origin;
    }

    if (meshData.vertices) {
      for (let key in meshData.vertices) {
        mesh.vertices[key] = meshData.vertices[key];
      }
    }

    if (meshData.faces) {
      for (let faceKey in meshData.faces) {
        const faceData = meshData.faces[faceKey];
        const face = new _MeshFace(mesh, faceData);

        if (meshData.texture && textureMap.has(meshData.texture)) {
          face.texture = textureMap.get(meshData.texture).uuid;
        }

        mesh.faces[faceKey] = face;
      }
    }

    mesh.init();
    mesh.addTo(parent);
    return mesh;
  }

  // ============================================================================
  // CODEC IMPLEMENTATION
  // ============================================================================

  class StrataCodec extends Codec {
    constructor() {
      super({
        name: 'Strata Binary Format',
        extension: 'strmodel',
        remember: false
      });
    }

    compile(options = {}) {
      const writer = new BinaryWriter();

      // Build string table
      const stringTable = [];
      const stringMap = new Map();

      function addString(str) {
        if (!stringMap.has(str)) {
          stringMap.set(str, stringTable.length);
          stringTable.push(str);
        }
        return stringMap.get(str);
      }

      // Collect all strings first
      const modelId = options.modelId || 'strata:model';
      addString(modelId);

      // Collect texture names
      const textureData = [];
      const textureIndexMap = new Map();

      Texture.all.forEach(tex => {
        const idx = addString(tex.name);
        textureIndexMap.set(tex.name, textureData.length);
        textureData.push({
          nameIndex: idx,
          width: tex.width || 16,
          height: tex.height || 16
        });
      });

      // Collect bones and elements
      const boneData = [];
      const meshData = [];
      const cuboidData = [];
      const boneIndexMap = new Map();

      function processBone(bone, parentIndex) {
        const boneIndex = boneData.length;
        boneIndexMap.set(bone.uuid, boneIndex);

        const nameIndex = addString(bone.name);
        const elements = [];

        // Process children
        for (const child of bone.children) {
          if (child instanceof _Cube) {
            // Cuboid
            const cuboidIndex = cuboidData.length;
            const texture = getElementTexture(child);
            const textureIndex = textureIndexMap.get(texture) || 0;

            const faces = [];
            const faceNames = ['north', 'south', 'east', 'west', 'up', 'down'];
            const faceIds = { north: 0, south: 1, east: 2, west: 3, up: 4, down: 5 };

            for (const faceName of faceNames) {
              const face = child.faces[faceName];
              if (face && face.texture !== null) {
                faces.push({
                  id: faceIds[faceName],
                  uv: face.uv || [0, 0, 16, 16],
                  rotation: face.rotation || 0
                });
              }
            }

            cuboidData.push({
              nameIndex: addString(child.name || 'cube'),
              textureIndex,
              origin: child.origin || [0, 0, 0],
              rotation: child.rotation || [0, 0, 0],
              from: child.from,
              to: child.to,
              inflate: child.inflate || 0,
              hidden: !child.visibility,
              faces
            });

            elements.push({ type: 1, index: cuboidIndex });

          } else if (child instanceof _Mesh) {
            // Mesh
            const meshIndex = meshData.length;
            const texture = getElementTexture(child);
            const textureIndex = textureIndexMap.get(texture) || 0;

            const vertices = [];
            const vertexMap = new Map();
            let vertexIndex = 0;

            for (const vkey in child.vertices) {
              vertexMap.set(vkey, vertexIndex++);
              vertices.push(child.vertices[vkey]);
            }

            const faces = [];
            for (const fkey in child.faces) {
              const face = child.faces[fkey];
              const faceVertices = [];

              for (const vkey of face.vertices) {
                const vIndex = vertexMap.get(vkey);
                const uv = face.uv[vkey] || [0, 0];
                faceVertices.push({ vertexIndex: vIndex, uv });
              }

              faces.push({ vertices: faceVertices });
            }

            meshData.push({
              nameIndex: addString(child.name || 'mesh'),
              textureIndex,
              origin: child.origin || [0, 0, 0],
              rotation: child.rotation || [0, 0, 0],
              hidden: !child.visibility,
              smooth: child.shading === 'smooth',
              vertices,
              faces
            });

            elements.push({ type: 0, index: meshIndex });
          }
        }

        boneData.push({
          nameIndex,
          parentIndex,
          pivot: bone.origin || [0, 0, 0],
          rotation: bone.rotation || [0, 0, 0],
          hidden: !bone.visibility,
          elements
        });

        // Process child bones
        for (const child of bone.children) {
          if (child instanceof Group) {
            processBone(child, boneIndex);
          }
        }
      }

      // Process bone hierarchy
      const rootBones = Outliner.root.filter(e => e instanceof Group);
      if (rootBones.length > 0) {
        processBone(rootBones[0], -1);
      }

      // Calculate bounding box
      const bbox = calculateModelBoundingBox();

      // Calculate string table size
      let stringTableSize = 0;
      for (const str of stringTable) {
        const encoder = new TextEncoder();
        stringTableSize += 2 + encoder.encode(str).length;
      }

      // Write header
      writer.writeFixedString('STRM', 4);
      writer.writeUInt16(200); // version 2.0
      writer.writeUInt16(0); // flags
      writer.writeUInt16(stringTable.length);
      writer.writeUInt16(textureData.length);
      writer.writeUInt16(boneData.length);
      writer.writeUInt16(meshData.length);
      writer.writeUInt16(cuboidData.length);
      writer.writeUInt32(stringTableSize);
      writer.writeVector3(bbox.min);
      writer.writeVector3(bbox.max);

      // Reserved bytes
      for (let i = 0; i < 18; i++) {
        writer.writeUInt8(0);
      }

      // Write string table
      for (const str of stringTable) {
        writer.writeString(str);
      }

      // Write textures
      for (const tex of textureData) {
        writer.writeUInt16(tex.nameIndex);
        writer.writeUInt16(tex.width);
        writer.writeUInt16(tex.height);
      }

      // Write bones
      for (const bone of boneData) {
        writer.writeUInt16(bone.nameIndex);
        writer.writeInt16(bone.parentIndex);
        writer.writeVector3(bone.pivot);
        writer.writeVector3(bone.rotation);
        writer.writeUInt8(bone.hidden ? 1 : 0);
        writer.writeUInt16(bone.elements.length);

        for (const elem of bone.elements) {
          writer.writeUInt8(elem.type);
          writer.writeUInt16(elem.index);
        }
      }

      // Write meshes
      for (const mesh of meshData) {
        writer.writeUInt16(mesh.nameIndex);
        writer.writeUInt16(mesh.textureIndex);
        writer.writeVector3(mesh.origin);
        writer.writeVector3(mesh.rotation);
        writer.writeUInt8((mesh.hidden ? 1 : 0) | (mesh.smooth ? 2 : 0));
        writer.writeUInt32(mesh.vertices.length);
        writer.writeUInt32(mesh.faces.length);

        for (const vertex of mesh.vertices) {
          writer.writeVector3(vertex);
        }

        for (const face of mesh.faces) {
          writer.writeUInt8(face.vertices.length);
          for (const fv of face.vertices) {
            writer.writeUInt32(fv.vertexIndex);
            writer.writeFloat32(fv.uv[0]);
            writer.writeFloat32(fv.uv[1]);
          }
        }
      }

      // Write cuboids
      for (const cuboid of cuboidData) {
        writer.writeUInt16(cuboid.nameIndex);
        writer.writeUInt16(cuboid.textureIndex);
        writer.writeVector3(cuboid.origin);
        writer.writeVector3(cuboid.rotation);
        writer.writeVector3(cuboid.from);
        writer.writeVector3(cuboid.to);
        writer.writeFloat32(cuboid.inflate);
        writer.writeUInt8(cuboid.hidden ? 1 : 0);
        writer.writeUInt8(cuboid.faces.length);

        for (const face of cuboid.faces) {
          writer.writeUInt8(face.id);
          writer.writeFloat32(face.uv[0]);
          writer.writeFloat32(face.uv[1]);
          writer.writeFloat32(face.uv[2]);
          writer.writeFloat32(face.uv[3]);
          writer.writeUInt8(face.rotation);
        }
      }

      return writer.toBuffer();
    }

    parse(buffer, path) {
      const reader = new BinaryReader(buffer);

      // Read header
      const magic = reader.readFixedString(4);
      if (magic !== 'STRM') {
        throw new Error('Invalid file format: magic number mismatch');
      }

      const version = reader.readUInt16();
      const flags = reader.readUInt16();
      const stringCount = reader.readUInt16();
      const textureCount = reader.readUInt16();
      const boneCount = reader.readUInt16();
      const meshCount = reader.readUInt16();
      const cuboidCount = reader.readUInt16();
      const stringTableSize = reader.readUInt32();
      const bboxMin = reader.readVector3();
      const bboxMax = reader.readVector3();

      // Skip reserved bytes
      for (let i = 0; i < 18; i++) {
        reader.readUInt8();
      }

      // Read string table
      const stringTable = [];
      for (let i = 0; i < stringCount; i++) {
        stringTable.push(reader.readString());
      }

      // Read textures
      const textures = [];
      const textureMap = new Map();

      for (let i = 0; i < textureCount; i++) {
        const nameIndex = reader.readUInt16();
        const width = reader.readUInt16();
        const height = reader.readUInt16();

        const texName = stringTable[nameIndex];
        const texture = new Texture({ name: texName, width, height }).add(false);
        textureMap.set(texName, texture);
        textures.push({ name: texName, texture });
      }

      // Read bones
      const bones = [];
      const boneElements = [];

      for (let i = 0; i < boneCount; i++) {
        const nameIndex = reader.readUInt16();
        const parentIndex = reader.readInt16();
        const pivot = reader.readVector3();
        const rotation = reader.readVector3();
        const flags = reader.readUInt8();
        const elementCount = reader.readUInt16();

        const elements = [];
        for (let j = 0; j < elementCount; j++) {
          const type = reader.readUInt8();
          const index = reader.readUInt16();
          elements.push({ type, index });
        }

        bones.push({
          name: stringTable[nameIndex],
          parentIndex,
          pivot,
          rotation,
          hidden: (flags & 1) !== 0
        });

        boneElements.push(elements);
      }

      // Read meshes
      const meshes = [];

      for (let i = 0; i < meshCount; i++) {
        const nameIndex = reader.readUInt16();
        const textureIndex = reader.readUInt16();
        const origin = reader.readVector3();
        const rotation = reader.readVector3();
        const flags = reader.readUInt8();
        const vertexCount = reader.readUInt32();
        const faceCount = reader.readUInt32();

        const vertices = {};
        const vertexKeys = [];
        for (let j = 0; j < vertexCount; j++) {
          const pos = reader.readVector3();
          const key = `v${j}`;
          vertices[key] = pos;
          vertexKeys.push(key);
        }

        const faces = {};
        for (let j = 0; j < faceCount; j++) {
          const faceVertCount = reader.readUInt8();
          const faceVerts = [];
          const faceUVs = {};

          for (let k = 0; k < faceVertCount; k++) {
            const vIndex = reader.readUInt32();
            const u = reader.readFloat32();
            const v = reader.readFloat32();

            const vKey = vertexKeys[vIndex];
            faceVerts.push(vKey);
            faceUVs[vKey] = [u, v];
          }

          faces[`f${j}`] = {
            vertices: faceVerts,
            uv: faceUVs
          };
        }

        meshes.push({
          type: 'blockbench_mesh',
          name: stringTable[nameIndex],
          texture: textures[textureIndex]?.name || 'untextured',
          origin,
          rotation,
          hidden: (flags & 1) !== 0,
          shade_smooth: (flags & 2) !== 0,
          vertices,
          faces
        });
      }

      // Read cuboids
      const cuboids = [];

      for (let i = 0; i < cuboidCount; i++) {
        const nameIndex = reader.readUInt16();
        const textureIndex = reader.readUInt16();
        const origin = reader.readVector3();
        const rotation = reader.readVector3();
        const from = reader.readVector3();
        const to = reader.readVector3();
        const inflate = reader.readFloat32();
        const flags = reader.readUInt8();
        const faceCount = reader.readUInt8();

        const faces = {};
        const faceNames = ['north', 'south', 'east', 'west', 'up', 'down'];

        for (let j = 0; j < faceCount; j++) {
          const faceId = reader.readUInt8();
          const u1 = reader.readFloat32();
          const v1 = reader.readFloat32();
          const u2 = reader.readFloat32();
          const v2 = reader.readFloat32();
          const rot = reader.readUInt8();

          faces[faceNames[faceId]] = {
            uv: [u1, v1, u2, v2],
            rotation: rot
          };
        }

        cuboids.push({
          type: 'blockbench_cuboid',
          name: stringTable[nameIndex],
          texture: textures[textureIndex]?.name || 'untextured',
          origin,
          rotation,
          from,
          to,
          inflate,
          hidden: (flags & 1) !== 0,
          faces
        });
      }

      // Build bone hierarchy
      const boneGroups = [];

      for (let i = 0; i < bones.length; i++) {
        const bone = bones[i];
        const group = new Group({
          name: bone.name,
          origin: bone.pivot,
          rotation: bone.rotation
        });

        if (bone.hidden) {
          group.visibility = false;
        }

        boneGroups.push(group);
      }

      // Set parent relationships
      for (let i = 0; i < bones.length; i++) {
        const bone = bones[i];
        if (bone.parentIndex >= 0 && bone.parentIndex < boneGroups.length) {
          const parent = boneGroups[bone.parentIndex];
          if (parent) {
            boneGroups[i].addTo(parent);
          } else {
            console.warn(`Bone ${i} (${bone.name}) has invalid parent index ${bone.parentIndex}`);
            boneGroups[i].addTo();
          }
        } else {
          boneGroups[i].addTo();
        }
      }

      // Add elements to bones
      for (let i = 0; i < boneElements.length; i++) {
        const elements = boneElements[i];
        const parent = boneGroups[i];

        for (const elem of elements) {
          if (elem.type === 0) {
            // Mesh
            const meshData = meshes[elem.index];
            createBlockbenchElement(meshData, parent, meshData.name, textureMap);
          } else {
            // Cuboid
            const cuboidData = cuboids[elem.index];
            createBlockbenchElement(cuboidData, parent, cuboidData.name, textureMap);
          }
        }
      }

      Canvas.updateAll();
    }
  }

  // ============================================================================
  // PLUGIN REGISTRATION
  // ============================================================================

  Plugin.register('strata_engine_format_binary', {
    title: 'Strata Engine Format (Binary)',
    author: 'SandyMandy',
    description: 'Binary format support for Strata Engine models',
    icon: 'icon-format_java',
    version: STRATA_FORMAT_VERSION,
    variant: 'both',

    onload() {
      // Create codec FIRST
      codec = new StrataCodec();

      // Create the Strata format
      format = new ModelFormat({
        id: 'strata_model_format_binary',
        name: 'Strata Model (Binary)',
        description: 'Binary model format for Strata Engine',
        icon: 'icon-format_java',
        category: 'minecraft',
        target: ['Modded Entity'],
        codec,
        meshes: true,
        bone_rig: true,
        centered_grid: true,
        rotate_cubes: true,
        stretch_cubes: true,
        per_texture_uv_size: true
      });

      // Model Export Action
      modelExportAction = new Action('export_strata_model_binary', {
        name: 'Export Strata Model (Binary)',
        icon: 'save',
        description: 'Export model to binary Strata Engine format',
        category: 'file',
        condition: () => Format === format,
        async click() {
          const rootBones = Outliner.root.filter(element => element instanceof Group);

          if (rootBones.length > 1) {
            Blockbench.showMessageBox({
              title: 'Export Error',
              message: `Strata models must have exactly one root bone. Your model currently has ${rootBones.length} root bones.`,
              icon: 'error'
            });
            return;
          }

          if (rootBones.length === 0) {
            Blockbench.showMessageBox({
              title: 'Export Error',
              message: 'No bones found! Strata models require at least one root bone.',
              icon: 'error'
            });
            return;
          }

          // Prompt for model ID
          const modelId = await new Promise(resolve => {
            const dialog = new Dialog({
              title: 'Model ID',
              form: {
                namespace: { label: 'Namespace', value: 'strata' },
                name: { label: 'Name', value: Project.name || 'model' }
              },
              onConfirm: (data) => {
                resolve(`${data.namespace}:${data.name}`);
                dialog.hide();
              },
              onCancel: () => {
                resolve(null);
                dialog.hide();
              }
            });
            dialog.show();
          });

          if (!modelId) return;

          const binaryData = codec.compile({ modelId });

          Blockbench.export({
            type: 'Strata Model',
            extensions: ['strmodel'],
            name: Project.name || 'model',
            content: binaryData,
            savetype: 'binary'
          });
        }
      });

      // Model Import Action
      modelImportAction = new Action('import_strata_model_binary', {
        name: 'Import Strata Model (Binary)',
        icon: 'folder_open',
        description: 'Import model from binary Strata Engine format',
        category: 'file',
        click() {
          Blockbench.import({
            extensions: ['strmodel'],
            type: 'Strata Model',
            readtype: 'binary'
          }, (files) => {
            files.forEach(file => {
              try {
                const buffer = new Uint8Array(file.content);
                newProject(format);
                codec.parse(buffer, file.path);
                Canvas.updateAll();
                Blockbench.showQuickMessage('Model imported successfully');
              } catch (error) {
                console.error('Import error:', error);
                Blockbench.showMessageBox({
                  title: 'Import Error',
                  message: 'Failed to import model: ' + error.message
                });
              }
            });
          });
        }
      });

      MenuBar.addAction(modelExportAction, 'file.export');
      MenuBar.addAction(modelImportAction, 'file.import');

      console.log('Strata Engine Binary Format plugin loaded successfully');
    },

    onunload() {
      codec.delete();
      format.delete();
      modelExportAction.delete();
      modelImportAction.delete();
    }
  });

})();