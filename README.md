# ComfyPort

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Support on Ko-fi](https://img.shields.io/badge/Support-Ko--fi-ff5e5b.svg?logo=kofi&logoColor=white)](https://ko-fi.com/comfyport)

**ComfyPort** is a fully native, high-performance Android client designed to interface with self-hosted ComfyUI servers, remote GPU rigs, and cloud instances.

ComfyUI is an exceptional node-based platform for generative AI on the desktop. However, interacting with dense node graphs through mobile web browsers presents significant ergonomic challenges—panning complex link topologies on touch screens often leads to accidental mutations and awkward text input.

I created ComfyPort to solve this: a purpose-built, touch-first mobile interface that connects directly to your existing ComfyUI server. It allows you to run, monitor, and tweak workflows natively on your phone or tablet with zero custom server plugins.


---

## Key Features

### Multi-Host & Remote Infrastructure
* **Direct Server Connectivity**: Seamless communication with any ComfyUI instance over local Wi-Fi, private mesh VPNs (such as **Tailscale** or WireGuard), or cloud reverse proxies (RunPod, Vast.ai).
* **Saved Server Profiles**: Label and store multiple endpoints (e.g., *Home Workstation*, *Tailscale Remote*, *Cloud GPU*) and switch active servers with a single tap.
* **Remote OpenSSH Rig Booter**: Boot your PC and launch ComfyUI remotely over SSH (via JSch), complete with a live in-app terminal console for streaming output and startup logs.

### Client-Side Workflow Engine
* **Zero Server Plugins Required**: Features an embedded client-side converter that executes ComfyUI's standard `graphToPrompt()` algorithm, converting raw LiteGraph UI workflows (`workflow.json`) into fully compliant API execution payloads on-device.
* **Automatic Topology Auto-Mapping**: Intelligently identifies positive/negative text prompts, latent dimensions, samplers, checkpoints, LoRAs, and output nodes in imported graphs.
* **Interactive 2D Graph Visualizer**: Pan-and-zoom node canvas with color-coded connection wiring (Latent, VAE, CLIP, Image, Model) and customizable widget parameter inspectors.
* **Server History Importer**: Queries your ComfyUI server's `/history` endpoint to inspect recent desktop runs and import their workflows directly to your mobile library.

### Mobile Generation & Creative Studio
* **Touch Inpainting Mask Studio**: Dedicated inpainting canvas featuring adjustable brush sizes, transparency controls, and instant PNG alpha mask encoding.
* **Aspect Ratio & Resolution Presets**: One-tap selectors for standard ratios (1:1, 4:3, 3:4, 16:9, 9:16, 21:9) with dynamic megapixel calculation and dimension scaling.
* **Real-Time Progress & Intermediate Previews**: Real-time progress indicators, step counters (e.g., `Step 14/25`), generation stopwatches, and intermediate step preview images streamed via persistent WebSockets.
* **Floating Queue Management**: Global floating badge displaying active queue counts, with bottom-sheet controls to clear pending batches or immediately abort active runs.

### Media Management & Portability
* **High-Performance Gallery**: Fast asynchronous image grid with disk caching powered by Coil.
* **Full-Resolution Viewer**: Pinch-to-zoom inspection with double-tap zoom reset.
* **Prompt & Seed Reloading**: Re-populate your prompt parameters and generation settings from any previously generated gallery item with a single tap.
* **Native Sharing & Export**: Export uncompressed images directly to the device's `Downloads` folder or share through the native Android Sharesheet.
* **Full Backup & Restore**: Export and restore your complete library of workflows and server profiles via JSON.

## Interface & Dynamic Workflow Engine

ComfyPort is not a rigid template that only works with a basic SD1.5 txt2img graph. It is a dynamic mobile interface built to handle your actual, real-world ComfyUI workflows:

### 1. One-Tap Desktop Run Importer
Stop emailing `.json` files or copying workflows over network shares. ComfyPort connects directly to your live machine's `/history` endpoint:
* Tap **Import Last Run** in the Workflow Manager.
* Instantly pulls whatever complex workflow (Flux, SDXL, Inpainting, ControlNet) was just executed on your desktop PC and loads it directly onto your phone.

### 2. Modular Node Mapping (Any Node, Any Workflow)
You have complete control over which parts of your graph appear on your mobile dashboard:
* **Auto-Mapped Essentials**: Positive & Negative prompts, Latent dimensions, Seed, Steps, and Samplers are automatically detected and surfaced as clean mobile controls.
* **Custom Parameter Mapping**: Tap any node in your graph to expose its widgets—tweak CFG scale, LoRA weights, denoise strength, checkpoint models, or custom node sliders without digging through node spaghetti.

### 3. Full Interactive 2D Graph Canvas
Want to double-check your connections before queuing?
* Open the **2D Graph Inspector** for a full interactive canvas.
* Pan, pinch-to-zoom, and inspect every node, connection wire (Latent, VAE, CLIP, Image, Model), and parameter directly on your phone or tablet.

---

## 📱 App Showcase

| Main Prompter | 2D Graph Inspector | Live Progress |
| :---: | :---: | :---: |
| <img src="screenshots/01_main_screen_portrait.png" width="240" alt="Main Screen"/> | <img src="screenshots/09_unfolded_tablet_split_view.png" width="240" alt="Graph Canvas"/> | <img src="screenshots/03_progress_screen_portrait.png" width="240" alt="Progress"/> |
| Auto-mapped and custom node controls. | Full interactive pan/zoom node topology. | Real-time percentage & intermediate step previews. |

| Inpainting Studio | Creations Gallery | Settings & Hosts |
| :---: | :---: | :---: |
| <img src="screenshots/06_creations_gallery_grid.png" width="240" alt="Gallery"/> | <img src="screenshots/07_gallery_details_modal.png" width="240" alt="Modal"/> | <img src="screenshots/02_settings_screen_portrait.png" width="240" alt="Settings"/> |
| Touch drawing canvas for mobile masks. | High-res grid with prompt reloading & download. | Multi-server IP manager & OpenSSH booter. |


---

## Getting Started

### 1. Server Configuration
Start your ComfyUI instance with the `--listen` argument to allow network connections:
```bash
python main.py --listen
```
*(If using the standalone portable Windows build, you can edit your `run_nvidia_gpu.bat` and append `--listen`.)*

### 2. Connecting the App
1. Open ComfyPort and navigate to **Settings** $\rightarrow$ **Saved Servers**.
2. Tap **Add Server** and enter your server's address:
   * **Local Network**: `http://192.168.1.xxx:8188`
   * **Tailscale (Recommended)**: `http://100.x.y.z:8188`
   * **Cloud GPU Proxy**: `https://<your-pod-id>-8188.proxy.runpod.net`
3. Tap **Test Connection** to verify reachability.

### 3. Downloading the App
Download the latest pre-compiled APK from the [Releases](https://github.com/your-username/ComfyPort/releases) page and install it on your Android device.

### 4. Building from Source
```bash
# Clone the repository
git clone https://github.com/your-username/ComfyPort.git
cd ComfyPort

# Build the debug APK
./gradlew assembleDebug

# Install to a connected device or emulator
./gradlew installDebug
```

---

## Project Development

This project started off from a lazy desire to generate images on my mobile device without having to transfer files back and forth on my computer, in a format that is both comfortable and easy. It has slowly evolved into something bigger, which is the reason I am publishing it. I also believe in full transparency, this project was vibe coded using antigravity.

---

## Support

If ComfyPort improves your workflow or makes managing your generative AI pipelines more accessible, consider supporting ongoing development:

[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20ComfyPort-ff5e5b?style=for-the-badge&logo=kofi&logoColor=white)](https://ko-fi.com/comfyport)

All donations will support me getting through college

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
