# SKYSIM FLOOR 7 SOURCE CODE
"*We recreated the Hypixel SkyBlock Floor 7 encounter for use within legacy SkySim Network projects. As we formally conclude development on the former project, we are making portions of this work available to the community so our players may continue experimenting with it and carry the original shenangians and random bulls**t forward.*" - GiaKhanhVN, Lead Developer of SkySim Network

**BOSSFIGHT GAMEPLAY DEMO VIDEOS (YOUTUBE):**
- https://www.youtube.com/watch?v=zXaBkVh9pBk (pre-release)
- https://www.youtube.com/watch?v=sgW4CUHZynw (production)

***We do not provide formal support for issues related to this source code, although exceptions may be considered on a case-by-case basis.***

## About the Wither Lords

The **Wither Lords Boss Fight** featuring Maxor, Storm, Goldor, and Necron represents the complete production implementation of Floor 7 as deployed on the SkySim Network. This repository is intended as a public reference for advanced boss fight design, gameplay orchestration, and large scale dungeon style encounters within a Hypixel-clone server environment.

The implementation follows established programming conventions and structured design patterns (i think). It is provided to support learning, research, and adaptation by the community.

The codebase is "well documented" (I have literally gone through the **entire god fucking damn codebase** and serveral layers of hell to **LITTER every single fucking line** with comments so the average Joe could grasp the concept easily), with adaquate coverage of internal architecture, control flow, algorithmic decisions, and design rationale across all phases of the fight. Make sure to read it. (*The code is not perfect, and some design or implementation decisions may be suboptimal or inefficient in certain areas.*)

## References

The Wither Lords Boss Fight is divided into four distinct phases. Below are the primary entry points and supporting components for each phase.

### **Maxor Phase (Phase 1, Monolith)**

* [Maxor Phase Implementation](src/vn/giakhanhvn/skysim/dungeons/bosses/witherlords/MaxorPhase.java)

### **Storm Phase (Phase 2, Monolith)**

* [Storm Phase Implementation](src/vn/giakhanhvn/skysim/dungeons/bosses/witherlords/StormPhase.java)

### **Goldor Phase (Phase 3, Modular)**

This phase introduces layered mechanics and coordinated subsystems, split across multiple components:

* [Goldor Phase Controller](src/vn/giakhanhvn/skysim/dungeons/bosses/witherlords/GoldorPhase.java) responsible for core phase flow and state management
* [Goldor Stage Controller](src/vn/giakhanhvn/skysim/dungeons/bosses/witherlords/GoldorStage.java) handling environmental logic including devices, terminals, and progression
* [Terminal Implementations](src/vn/giakhanhvn/skysim/dungeons/bosses/witherlords/utils/terminals/) containing terminal specific mechanics, with shared utilities located in [utils](src/vn/giakhanhvn/skysim/dungeons/bosses/witherlords/utils/)

### **Necron Phase (Final Phase, Monolith)**

* [Necron Phase Implementation](src/vn/giakhanhvn/skysim/dungeons/bosses/witherlords/NecronPhase.java)

---

© 2025 GiaKhanhVN, SkySim Network. All rights reserved.
