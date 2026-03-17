# MASL: An After Story Launcher (Unofficial Android Port)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/platform-Android-blue.svg)]()

> **STRICT LEGAL DISCLAIMER & IMPORTANT NOTICE**
> 
> **1. NO ASSETS INCLUDED:** This repository does **not** contain any game assets, artwork or music from *Doki Doki Literature Club!* (DDLC) or the *Monika After Story* (MAS) modification.
>
> **2. UNOFFICIAL PROJECT:** This is a strictly unofficial, fan-made Android wrapper/launcher. It is not affiliated with, endorsed by, or associated with Team Salvato or the official Monika After Story development team. 
>
> **3. SCOPE:** This repository solely contains the Android project structure, the Ren'Py engine wrapper, and custom tools designed to facilitate the execution of legally obtained mod files on Android devices.

## Overview

MASL (An After Story Launcher) is a custom-engineered Android port designed to run the Monika After Story mod seamlessly on mobile environments. Rather than distributing copyrighted material, this project provides a robust, optimized launcher based on a customized fork of Ren'Py's RAPT (Ren'Py Android Packaging Tool).

## Technical Architecture

This project is built upon a modified RAPT environment to ensure compatibility and stability on modern Android architectures.

### The `unrpa` Kotlin Implementation

To handle Ren'Py Archive (`.rpa`) extraction natively within the Android environment, we have developed a custom `unrpa` utility written in Kotlin. This implementation is designed for performance and seamless integration with the Android file system.

* **Logic Attribution:** The core structural logic for the `unrpa` utility is heavily based on the original Python implementation by Lattyware. 
* **Reference:** [Lattyware/unrpa](https://github.com/Lattyware/unrpa)

## Acknowledgments & Credits

* **[Team Salvato](https://teamsalvato.com/):** For creating the incredible *Doki Doki Literature Club!*
* **[Monika After Story Team](https://www.monikaafterstory.com/):** For the original mod and their continuous hard work.
* **[Ren'Py](https://www.renpy.org/):** For the visual novel engine and the RAPT framework.
* **Lattyware:** For the foundational `unrpa` logic.

---
*Developed and maintained by Traduction Club!*
