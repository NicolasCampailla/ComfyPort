# ComfyPort

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Support on Ko-fi](https://img.shields.io/badge/Support-Ko--fi-ff5e5b.svg?logo=kofi&logoColor=white)](https://ko-fi.com/comfyport)

ComfyPort is a fully native, high-performance Android client designed to interface directly with self-hosted ComfyUI servers, remote GPU rigs, and cloud instances.

ComfyUI is an exceptional platform for generative AI on desktop environments. However, interacting with dense node graphs through mobile web browsers presents serious usability challenges: manipulating complex connection topologies on touch displays frequently causes accidental node mutations, missed connections, and awkward parameter adjustment.

ComfyPort solves this problem with a touch-first native interface that connects directly to your existing ComfyUI server. Run, monitor, and adjust your workflows on your phone or tablet with zero custom server plugins.

---

## Workflow Interface & Architecture

ComfyPort is designed to handle arbitrary real-world graphs rather than restricting users to rigid templates:

### 1. One-Tap Desktop Run Importer
Directly queries your server's `/history` endpoint:
* Tap **Browse Machine Workflow History** in the Workflow Manager and choose a recently run workflow.
* Instantly loads the exact configuration (Flux, SDXL, Inpainting, ControlNet, or custom pipelines) recently executed on your desktop directly into your mobile session.

### 2. Modular Node Mapping
Take control over which parts of your graph appear on your mobile dashboard:
* **Automatic Detection**: Positive and negative text prompts, latent dimensions, seed, image and mask loaders are automatically detected and surfaced as clean mobile input fields.
* **Custom Parameter Mapping**: Tap any node in your graph and set it as an input to modify its parameters directly from the main screen. Adjust CFG scales, LoRA weights, denoise levels and custom node sliders without navigating visual node spaghetti.

### 3. Interactive 2D Graph Canvas
Verify your execution logic before queuing generations:
* Open the **2D Graph Inspector** for a comprehensive visual canvas.
* Pan, pinch-to-zoom, and inspect every node, connection wire (Latent, VAE, CLIP, Image, Model), and parameter directly on your device.

---

## Interface Showcase

| Modular Node Mapping | Interactive 2D Graph | Multi-Server Manager |
| :---: | :---: | :---: |
| <img src="screenshots/custom_input.jpg" height="420" alt="Workflow Parameters"/> | <img src="screenshots/workflow_preview.jpg" height="420" alt="Interactive Graph Canvas"/> | <img src="screenshots/servers.jpg" height="420" alt="Saved Servers"/> |
| Custom widget parameters surfaced directly on your mobile dashboard. | Full pan-and-zoom node topology to inspect connections on the go. | Manage local Wi-Fi, Tailscale VPN, and Cloud GPU endpoints. |

| Remote OpenSSH Booter | Live Progress & Previews | Touch Inpainting Studio |
| :---: | :---: | :---: |
| <img src="screenshots/ssh.jpg" height="420" alt="SSH Rig Booter"/> | <img src="screenshots/generation.jpg" height="420" alt="Live Progress Screen"/> | <img src="screenshots/masking.jpg" height="420" alt="Touch Inpainting Studio"/> |
| Boot your PC and launch ComfyUI remotely with an interactive console. | Real-time generation progress, stopwatch, and active step tracking. | Dedicated drawing canvas with adjustable brushes for alpha mask generation. |

---

## Key Features

### Multi-Host & Remote Infrastructure
* **Direct Server Connectivity**: Connects to any ComfyUI instance over local Wi-Fi, private mesh VPNs (such as Tailscale or WireGuard), or cloud reverse proxies (RunPod, Vast.ai).
* **Saved Server Profiles**: Label and store multiple endpoints (e.g., Home Workstation, Tailscale Remote, Cloud GPU) and switch active servers with a single tap.
* **Remote OpenSSH Rig Booter**: Boot your PC and start ComfyUI remotely over SSH (via JSch), complete with a live terminal console for streaming console output and startup logs.

### Client-Side Workflow Engine
* **Zero Server Plugins Required**: Includes an embedded client-side converter that executes ComfyUI's standard `graphToPrompt()` algorithm, transforming raw LiteGraph UI workflows (`workflow.json`) into fully compliant API execution payloads on-device.
* **Automatic Topology Auto-Mapping**: Intelligently identifies positive/negative prompts, latent dimensions, samplers, checkpoints, LoRAs, and output nodes in imported graphs.
* **Server History Importer**: Queries your ComfyUI server's `/history` endpoint to inspect recent desktop runs and import their workflows directly to your mobile library.

### Mobile Generation & Creative Studio
* **Touch Inpainting Mask Studio**: Dedicated inpainting canvas featuring adjustable brush sizes, transparency controls, and instant PNG alpha mask encoding.
* **Aspect Ratio & Resolution Presets**: Fast selectors for standard ratios (1:1, 4:3, 3:4, 16:9, 9:16, 21:9) with dynamic megapixel calculation and dimension scaling.
* **Real-Time Progress & Intermediate Previews**: Real-time progress indicators, step counters (e.g., Step 14/25), generation stopwatches, and intermediate step preview images streamed via persistent WebSockets.
* **Floating Queue Management**: Global floating badge displaying active queue counts, with bottom-sheet controls to clear pending batches or immediately abort active runs.

### Media Management & Portability
* **High-Performance Gallery**: Fast asynchronous image grid with disk caching powered by Coil.
* **Full-Resolution Viewer**: Pinch-to-zoom inspection with double-tap zoom reset.
* **Prompt & Seed Reloading**: Re-populate your prompt parameters and generation settings from any previously generated gallery item with a single tap.
* **Native Sharing & Export**: Export uncompressed images directly to the device's Downloads folder or share through the native Android Sharesheet.
* **Encrypted Full Backup & Restore**: Export and restore your complete library of workflows and server profiles via JSON.

### Privacy, Security & Design
* **OLED Dark Aesthetic**: Interface designed specifically for OLED displays, with customizable accent highlight colors (Emerald, Electric Cyan, Neon Purple, Sunset Orange, Hot Pink, or custom HEX).
* **Hardware-Backed Cryptography**: All SSH passwords and server credentials are cryptographically protected using Android's `EncryptedSharedPreferences` (AES-256 GCM).
* **Direct & Telemetry-Free**: 100% direct client-to-server communication. No analytics SDKs, no tracking, and no intermediary relay servers.

---

## Getting Started & Tutorial

### 1. Server Configuration
Start your ComfyUI instance with the `--listen` argument to allow incoming network connections:
```bash
python main.py --listen
```
*(If using the standalone portable Windows build, you can append `--listen` to your `run_nvidia_gpu.bat` script.)*

### 2. Install ComfyPort (Download or Build)
You can install the pre-compiled package directly or build from source:
* **Download APK**: Grab the latest release package (`ComfyPort-v1.1.1.apk`) from the [Releases](https://github.com/NicolasCampailla/ComfyPort/releases) page and install it on your Android device (Android 8.0+).
* **Build from Source**:
  ```bash
  git clone https://github.com/NicolasCampailla/ComfyPort.git
  cd ComfyPort
  ./gradlew assembleDebug
  ./gradlew installDebug
  ```

### 3. Add New IP Address
1. Open ComfyPort and navigate to **Settings** -> **Saved Servers**.
2. Tap **Add New IP Address** and enter your server endpoint:
   * **Local Network**: `http://192.168.1.xxx:8188`
   * **Tailscale (Recommended)**: `http://100.x.y.z:8188` (enables secure, direct access outside your home with no router port forwarding)
   * **Cloud GPU Proxy**: `https://<your-pod-id>-8188.proxy.runpod.net`
3. Tap **Test Connection** to confirm connectivity, then tap the server card to activate it.

### 4. Optional: Remote OpenSSH Booting
If you want to keep your desktop rig powered off or asleep when not generating:
* Navigate to **Settings** -> **Remote Server (OpenSSH)**.
* Enter your machine's SSH host (local or Tailscale IP), port 22, and user credentials.
* Tap the power button on the home screen to boot up ComfyUI remotely, with live startup logs streamed directly into the in-app terminal viewer.

### 5. Import Your Workflows (Local Files & Machine History)
ComfyPort lets you bring workflows onto mobile without manual file conversion:
* **Import from Machine History**: In the **Workflow Manager**, tap **Import Last Run** (or browse recent desktop runs via the history dialog). ComfyPort connects directly to your server's `/history` endpoint and pulls in the workflow you just ran on your desktop.
* **Import Local JSON**: Tap **Import Workflow (.json)** to load any exported ComfyUI LiteGraph workflow file stored on your phone.

### 6. Preview the Graph, Map Inputs, and Generate
Once your workflow is loaded, customize how you interact with it:
* **Preview the 2D Graph Canvas**: Tap the workflow preview icon to open the full interactive 2D node inspector. Pan, zoom, and inspect every node, connection wire (Latent, VAE, CLIP, Image, Model), and parameter.
* **Map Modular Inputs**: ComfyPort automatically maps standard prompts, dimensions, and sampler settings on first load. You may remap those manually anytime. To expose custom parameters (such as LoRA weights, denoise sliders, etc...), tap any node and select **input** from the bottom bar. You can then modify those values in the prompt tab.
* **Generate**: Enter your prompt, tweak your mapped controls, and tap **Generate** to watch live step progress and intermediate preview images stream in real time.

---

## Technology Stack

* **UI Framework**: Jetpack Compose & Material 3 (OLED Dark Mode)
* **Architecture**: Modern Android Architecture with Kotlin Coroutines & Flow
* **Networking**: OkHttp 4 with persistent Duplex WebSockets
* **Remote Management**: JSch OpenSSH client with interactive PTY terminal streaming
* **Image Loading**: Coil 2.7 with disk and memory caching
* **Security**: Android Jetpack Security (`EncryptedSharedPreferences` with AES-256 GCM)

---

## Project Background

This project began from a practical desire: to generate images on a mobile device without having to constantly transfer files back and forth with a desktop computer, using an interface that feels fast, natural, and comfortable on a touch screen. Over time, it evolved into a full-featured client capable of handling complex production workflows, which is why I decided to open-source it for the community.

In the spirit of full transparency, ComfyPort was built in pair-programming collaboration with Antigravity, an agentic AI coding assistant developed by Google DeepMind.

---

## Support

If ComfyPort improves your workflow or makes managing your generative AI pipelines more accessible, consider supporting ongoing development:

[![Support on Ko-fi](https://img.shields.io/badge/Support-Ko--fi-ff5e5b.svg?logo=kofi&logoColor=white)](https://ko-fi.com/comfyport)

All donations directly support me as a student working my way through college.

---

## License

This project is licensed under the [MIT License](LICENSE).
