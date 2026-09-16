# MASL: An After Story Launcher (Unofficial Android Port)

> [!IMPORTANT]
> **NO ASSETS INCLUDED:** This repository does **not** contain any game assets, artwork, or music from *Doki Doki Literature Club!* (DDLC) or *Monika After Story* (MAS) mod.
> **UNOFFICIAL PROJECT:** This is a strictly unofficial, fan-made Android wrapper/launcher. It is not affiliated with, endorsed by, or associated with Team Salvato or the official Monika After Story development team.

---

## Overview

**MASL (An After Story Launcher)** is a custom Android port designed to run the *Monika After Story* mod seamlessly on mobile environments. Built upon a highly customized fork of Ren'Py's RAPT (Ren'Py Android Packaging Tool), MASL provides a native-like experience with advanced features specifically tailored for the MAS community.

## Key Features

- **One-Click Installation:** Automatic download and setup of MAS mod.
- **Content Installers:** Built-in installers for **Spritepacks** and **Submods**.
- **Discord Rich Presence:** Using integrated [KizzyRPC](https://github.com/KizzyRPC). (Still working on that lol)
- **File Explorer:** Integrated file manager to handle game files, submods, etc.
- **Multi-Language Support:** Fully localized in English, Español, and Português.
- **Optimized Engine:** Custom Kotlin-based `unrpa` implementation and stockfish 8 library.
- **Cool UI:** A launcher based in another cool UI...

## Installation

To use MASL, you must provide your own legally obtained copy of DDLC and the MAS mod.

1. **Download DDLC:** Obtain the Windows version of Doki Doki Literature Club from [ddlc.moe](https://ddlc.moe/).
2. **Download MAS:** MASL will handle it for you, using latest release of [this MAS](https://github.com/New-Traduction-Club/MonikaModDev-Unofficial-Android/releases).

## Components
- **Kotlin Integration:** Native Android components handle heavy lifting like ZIP extraction, RPA unpacking, Discord RPC, UI for Piano, notifications, etc.
- **Internal Storage Provider:** A custom DocumentsProvider to allow external apps to interact with game files securely.

### The `unrpa` Kotlin Implementation
To handle Ren'Py Archive (`.rpa`) extraction natively, we developed a custom `unrpa` utility in Kotlin.
* **Logic Attribution:** Based on the Python implementation by [Lattyware](https://github.com/Lattyware/unrpa).

# MINE

**MINE (MASL Is Not an Emulator)** is a multi-runtime Ren'Py game launcher and environment integrated into MASL.

MINE executes Ren'Py games natively using custom builds (except 8.4.1 & 8.5.3) of Ren'Py runtimes.

Versions available:

- Python 2: Ren'Py 6.99.14, 7.4.11, 7.8.4
- Python 3: Ren'Py 8.0.3, 8.3.7, 8.4.1, 8.5.3

## Credits

*   **[Team Salvato](https://teamsalvato.com/):** For creating the incredible *Doki Doki Literature Club!*
*   **[Monika After Story Team](https://www.monikaafterstory.com/):** For the original mod and their continuous hard work.
*   **[Ren'Py](https://www.renpy.org/):** For the visual novel engine.
*   **[KizzyRPC](https://github.com/KizzyRPC):** For the Android Discord RPC implementation.

---
*Developed and maintained by Traduction Club!*
