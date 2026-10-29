# litespat

litespat is a one-click Higher-Order Ambisonics (HOA) renderer for a speaker array on Ubuntu.
It runs a SuperCollider encoder for up to 32 mono sources that are positioned over OSC,
decodes the result to the speakers with the IEM AllRADecoder, and wires all the audio
connections automatically. Clicking a desktop icon starts or stops the whole thing, and it
can also start at login.

It is meant as a shared backend. A project can either send mono audio and positions to
litespat, or send an already-encoded HOA stream (for example from a DAW running IEM
encoders) straight into the same decoder.

---

## Contents

1. [Signal chain](#signal-chain)
2. [What runs](#what-runs)
3. [Requirements](#requirements)
4. [Installation](#installation)
5. [First-time decoder setup](#first-time-decoder-setup)
6. [Configuring the connections](#configuring-the-connections)
7. [Everyday use](#everyday-use)
8. [Sending positions over OSC](#sending-positions-over-osc)
9. [Feeding litespat from other software](#feeding-litespat-from-other-software)
10. [Configuration reference](#configuration-reference)
11. [Files](#files)
12. [Troubleshooting](#troubleshooting)
13. [Uninstalling](#uninstalling)
14. [Known limitations](#known-limitations)
15. [Notes for maintainers](#notes-for-maintainers)

---

## Signal chain

```
 mono sources ──▶ SuperCollider ─────────────▶ IEM AllRADecoder ──▶ audio interface ──▶ speakers
 (JACK ports)     32 × HoaEncodeDirection   ▲  (speaker layout)     (Pro Audio profile)
                  order 5 → 36 channels     │
 OSC :6666 ──────▶ azimuth / elevation /    │
                  distance per source       │
                                            │
 encoded HOA from a DAW (optional) ─────────┘
```

The tested setup is:

```
Ubuntu PC ──USB──▶ Klark Teknik KT-USB Pro ──AES50──▶ Midas M32 ──▶ speaker array
```

| Stage | Detail |
|---|---|
| Encoder | SuperCollider with the Ambisonic Toolkit (ATK), `HoaEncodeDirection`, order 5 |
| HOA format | 36 channels, **ACN** channel order, **N3D** normalization |
| Decoder | IEM AllRADecoder standalone (`iem-plugin-allradecoder`), input set to N3D |
| Audio system | PipeWire, with every program run through its JACK layer (`pw-jack`) |
| Control | OSC on UDP port **6666** |

---

## What runs

litespat installs four systemd **user** units. They are user units, not system units,
because they need the logged-in user's PipeWire session and a screen for the decoder's
window.

| Unit | Type | What it does |
|---|---|---|
| `litespat.target` | target | Groups the three services. Starting or stopping it starts or stops all of them. |
| `litespat-sc.service` | simple | Runs `sclang -D litespat.scd` with no window. Boots the SuperCollider server, creates 32 encoders and listens for OSC. |
| `litespat-decoder.service` | simple | Runs the IEM decoder standalone. Its window opens on the desktop. |
| `litespat-connect.service` | oneshot | Waits until SuperCollider's and the decoder's ports exist, clears unwanted automatic connections, then connects everything in order. |

SuperCollider and the decoder restart automatically if they crash, but they give up after 5
failed starts within a minute instead of retrying forever. See
[Troubleshooting](#troubleshooting).

---

## Requirements

Ubuntu (tested on a KDE desktop) running **PipeWire** with:

```bash
sudo apt install supercollider sc3-plugins pipewire-jack jackd2 iem-plugin-suite-standalone liblo-tools
```

- `pipewire-jack` provides `pw-jack`.
- `jackd2` provides `jack_lsp`, `jack_connect` and `jack_disconnect`. On a PipeWire system
  these are used through `pw-jack`, and `jackd` itself is never started.
- `iem-plugin-suite-standalone` provides `/usr/bin/iem-plugin-allradecoder`. The `.vst3`
  and `.so` plugin files on their own are **not** enough, because they need a host
  program to run in.
- `liblo-tools` provides `oscsend`, which is handy for testing.

Install the Ambisonic Toolkit quark once, from the SuperCollider IDE:

```supercollider
Quarks.install("atk-sc3");
// recompile the class library (Ctrl+Shift+L), then:
Atk.createUserSupportDir;
Atk.downloadKernels;
Atk.downloadMatrices;
```

Your user should be in the `audio` group:

```bash
sudo usermod -aG audio $USER   # then log out and back in
```

---

## Installation

Copy the `litespat` folder to the machine and run:

```bash
bash litespat/install.sh                 # install; start and stop with the icon
bash litespat/install.sh --enable-boot   # also start rendering at every desktop login
bash litespat/install.sh --disable-boot  # stop starting at login
```

No `sudo` is needed. Running it again is safe: it updates the scripts and units, and it
**never overwrites an existing `~/.config/litespat/litespat.conf`**. After changing the
installed units by hand, run `systemctl --user daemon-reload`.

The installer warns about any missing commands and if `DECODER_BIN` doesn't exist.

> Turn on `--enable-boot` only after the decoder setup and connections below work.
> Otherwise a mistake will repeat at every login.

---

## First-time decoder setup

The decoder standalone saves its own settings, so on **each new machine** run it once by hand:

```bash
pw-jack iem-plugin-allradecoder
```

1. **Options → Audio/MIDI Settings:** set the device type to **JACK**, and enable **all**
   input and output channels. If you skip this, the decoder runs as a 2-channel ALSA
   program, shown in `jack_lsp` as `PipeWire ALSA [iem-plugin-allradecoder] … output_FL/FR`.
2. **Input:** set order **5** and normalization **N3D**. ATK produces N3D, and IEM defaults to
   SN3D, so leaving it on SN3D gives the wrong spatial image.
3. **Layout:** **Import** the room's speaker layout (`.json`). Check that each speaker's
   **channel number** matches the interface output that feeds it, then click
   **Calculate Decoder**.
4. Close the window with its **X** button. This saves the settings, and because it's a normal
   exit, systemd does not restart it.

The decoder remembers only one layout. When you move between rooms, import that room's
layout, calculate again and close the window.

For headphone monitoring, use `iem-plugin-binauraldecoder` instead and set `DECODER_BIN` to it.

---

## Configuring the connections

The connect script matches JACK port names with regular expressions from
`~/.config/litespat/litespat.conf`. These depend on the machine and the interface, so set
them once per setup.

1. Set the audio interface to the **Pro Audio** profile (KDE: System Settings → Sound →
   device → Profile), so that every channel appears as its own port (`playback_AUX0`, `AUX1`, …).
2. Start litespat, then list the ports:

   ```bash
   pw-jack jack_lsp | grep -i allra                      # decoder inputs and outputs
   pw-jack jack_lsp | grep -i playback | grep -iv alc    # interface outputs
   ```

3. Edit the config:

   ```bash
   nano ~/.config/litespat/litespat.conf
   ```

   ```bash
   DECODER_IN_REGEX='^<decoder client>:<input prefix>'
   DECODER_OUT_REGEX='^<decoder client>:<output prefix>'
   SPEAKER_REGEX='^<interface name>:playback_'
   ```

   Use `[.]` instead of `\.` for a literal dot.

4. Preview the result without changing anything:

   ```bash
   litespat-connect --dry-run
   ```

   It prints every planned connection, for example
   `<decoder>:out_1 -> <interface>:playback_AUX0`. Ports are paired **in the order JACK
   lists them**: the 1st decoder output goes to the 1st speaker port, and so on.

5. Restart litespat, then check the result in **QjackCtl → Graph**:

   ```bash
   litespat-toggle stop && litespat-toggle start
   ```

What the connect script does, in order:

1. Waits up to `PORT_TIMEOUT` seconds for SuperCollider's and the decoder's ports.
2. Removes every existing connection on SuperCollider's inputs and outputs and on the
   decoder's outputs. This stops raw HOA channels from going straight to the speakers and
   stops microphones leaking into the encoders.
3. Connects `SOURCE_REGEX` → SuperCollider inputs (if set).
4. Connects SuperCollider outputs → decoder inputs.
5. Connects `AMBI_SOURCE_REGEX` → decoder inputs (if set). JACK sums this with SuperCollider.
6. Connects decoder outputs → `SPEAKER_REGEX`.

Connections are made **once, at start**. A program that joins later (a DAW, or a JackTrip
peer) is not connected automatically: start it first, or restart litespat afterwards.

---

## Everyday use

| Action | How |
|---|---|
| Start / stop | Click the **litespat** icon (toggles), or right-click → Start / Stop rendering |
| From a terminal | `litespat-toggle start`, `stop`, `toggle` |
| Check health | `litespat-toggle status` |
| Live logs | `journalctl --user -u 'litespat*' -f` |
| Logs since boot | `journalctl --user -u 'litespat*' -b` |
| Re-apply connections | `systemctl --user restart litespat-connect` |

A healthy `litespat-toggle status` looks like:

```
litespat.target    active     restarts=
litespat-sc        active     restarts=0
litespat-decoder   active     restarts=0
litespat-connect   active     restarts=0
```

`litespat.target` reads `active` even when a service under it is failing, so check each line.
Any service that isn't `active`, or has a restart count above 0, needs attention.

The decoder's window appears whenever litespat runs. You can minimize it, and rendering continues.

> `~/.local/bin` is only added to your command path at login. Straight after the first
> install, use `~/.local/bin/litespat-toggle` until you log out and back in.

---

## Sending positions over OSC

litespat listens on UDP port **6666**. The source index is an **integer** from 0 to 31, and
the value is a float.

| Address | Arguments | Meaning |
|---|---|---|
| `/source/azim` | `int index, float radians` | Azimuth, counter-clockwise from the front (ATK convention) |
| `/source/elev` | `int index, float radians` | Elevation, positive upward |
| `/source/dist` | `int index, float metres` | Distance (default 1.5) |

Examples:

```bash
oscsend localhost 6666 /source/azim if 0 1.5708   # source 0 to the left
oscsend localhost 6666 /source/elev if 0 0.5
oscsend localhost 6666 /source/dist if 0 2.0
```

Indices outside 0–31 are ignored, with a message in the log.

Things to know:

- **Angles are radians.** Convert degrees first (in SuperCollider, `deg.degrad`).
- **Distance changes tone, not loudness.** ATK uses the radius for near-field filtering:
  sources closer than 1.5 m get a low-frequency boost. Keep it at about 1 m or more, and
  do distance attenuation in the sending program if you need it.
- **Send the index as an integer.** Some controllers send floats by default.
- Sending positions at high rates is fine. Positions update in steps, so very fast movement
  can produce slight zipper noise. Smooth positions in the sender if that's a problem.

---

## Feeding litespat from other software

**Mono sources with OSC positions.** Any JACK program with one output per source can feed
the encoders. Set `SOURCE_REGEX` to its output ports, and output *n* goes to source *n−1*.
For example, a JackTrip receiver:

```bash
SOURCE_REGEX='^JackTrip:receive_'
```

A second SuperCollider server (such as a separate sound engine) has to use a different
server port from litespat's. JACK then names it something like `SuperCollider-01`.

**Already-encoded HOA (IEM encoders in a DAW).** Send the DAW's 36-channel ACN/N3D bus straight
to the decoder by setting:

```bash
AMBI_SOURCE_REGEX='^<daw client>:<output prefix>'
```

If the DAW's encoders use SN3D, set them to N3D to match the decoder's input setting.

---

## Configuration reference

`~/.config/litespat/litespat.conf` is read by both the systemd units and the connect script.
After editing it, run `systemctl --user restart litespat.target`.

| Setting | Default | Meaning |
|---|---|---|
| `DECODER_BIN` | `/usr/bin/iem-plugin-allradecoder` | Decoder program to launch |
| `SC_CLIENT` | `SuperCollider` | SuperCollider's JACK client name |
| `DECODER_IN_REGEX` | `'^AllRADecoder:in'` | Decoder input ports. **Check on your machine.** |
| `DECODER_OUT_REGEX` | `'^AllRADecoder:out'` | Decoder output ports. **Check on your machine.** |
| `SPEAKER_REGEX` | `'^alsa_output[.].*:playback_'` | Interface output ports, in speaker order |
| `SOURCE_REGEX` | empty | Mono sources → SuperCollider inputs |
| `AMBI_SOURCE_REGEX` | empty | Encoded HOA → decoder inputs |
| `PORT_TIMEOUT` | `30` | Seconds to wait for ports before giving up |
| `PIPEWIRE_QUANTUM` | commented out | Fixed buffer size and rate, for example `256/48000` |

Settings inside `litespat.scd` (edit `~/.local/share/litespat/litespat.scd`, then restart):

| Variable | Default | Meaning |
|---|---|---|
| `~osc_IN` | `6666` | OSC port |
| `~n_inputs` | `32` | Number of sources and encoder inputs |
| `~hoa_order` | `5` | Ambisonic order. Channels = (order+1)², so 36 at order 5. |
| output gain | `0.75` | Set on `~hoa_output` |

Order 5 decodes fine to smaller arrays. As a rule of thumb, an array of *N* speakers resolves
about order √N − 1: order 3 for 15 speakers, order 4 for 29.

---

## Files

Source folder:

```
litespat/
├── README.md
├── install.sh              installer (user-level, no sudo)
├── litespat.scd            SuperCollider encoder script (headless)
├── litespat.conf           default configuration
├── litespat-connect.sh     JACK wiring script (supports --dry-run)
├── litespat-toggle.sh      start / stop / toggle / status
├── systemd/
│   ├── litespat.target
│   ├── litespat-sc.service
│   ├── litespat-decoder.service
│   └── litespat-connect.service
└── desktop/
    └── litespat.desktop    launcher template (@HOME@ replaced at install)
```

Installed locations:

| Path | Contents |
|---|---|
| `~/.local/share/litespat/litespat.scd` | Encoder script |
| `~/.local/bin/litespat-connect`, `litespat-toggle` | Scripts |
| `~/.config/systemd/user/litespat*` | systemd units |
| `~/.config/litespat/litespat.conf` | Your configuration (kept on reinstall) |
| `~/.local/share/applications/litespat.desktop` | Menu entry |
| `~/Desktop/litespat.desktop` | Desktop icon |

The icon is a standard Linux `.desktop` launcher. Its `Exec` line runs `litespat-toggle`, and
its `Actions` give the right-click Start and Stop entries.

---

## Troubleshooting

Start with `litespat-toggle status`, then read the failing unit's log:

```bash
journalctl --user -u litespat-decoder -n 30 --no-pager
```

| Symptom | Cause | Fix |
|---|---|---|
| Decoder log: `exec: /usr/bin/… not found`, status 127 | `DECODER_BIN` points at a program that doesn't exist | Fix the path in the config, then `systemctl --user reset-failed 'litespat*'` and restart |
| `Start request repeated too quickly` | A service failed 5 times in a minute and systemd stopped retrying | Fix the cause, then `systemctl --user reset-failed 'litespat*'` |
| `litespat-connect` failed, decoder running | Decoder ports don't match `DECODER_*_REGEX`, or the decoder isn't on JACK | Check `pw-jack jack_lsp \| grep -i allra`, then fix the regexes or switch the decoder to JACK |
| Decoder ports are `PipeWire ALSA […] output_FL/FR` | The decoder is using ALSA stereo, not JACK | Do the [first-time decoder setup](#first-time-decoder-setup) |
| Everything runs, no sound | Speaker ports not connected, or a problem after the computer | `litespat-connect --dry-run`, then check meters along the chain |
| No meters on the mixer | Signal isn't crossing USB or AES50 | `pw-top`: the interface should be running at 48000 with ERR near 0. Check the interface's AES50 **lock** light, the clock source on the mixer, matching sample rates, and that the mixer routes those AES50 inputs to channels. |
| Only 2 interface channels | The interface isn't in the **Pro Audio** profile | Change the profile in KDE Sound settings |
| `litespat-toggle: command not found` | `~/.local/bin` isn't on your path yet | Use `~/.local/bin/litespat-toggle`, or log out and back in |
| Crackles or drop-outs | Buffer too small or a sample-rate mismatch | Set `PIPEWIRE_QUANTUM=256/48000` in the config |
| Raw HOA channels heard on the speakers | SuperCollider auto-connected to the hardware | Restart `litespat-connect`, which clears those connections |
| Wrong speaker plays during the decoder's noise test | Channel numbers in the layout don't match the interface or mixer routing | Fix the channel numbers in AllRADecoder, or the routing on the mixer |

A useful first test that leaves litespat out completely: play audio in a browser, connect it
to the interface's first two playback ports in QjackCtl, and watch the mixer's AES50 meters.

---

## Uninstalling

```bash
systemctl --user disable --now litespat.target
rm -f ~/.config/systemd/user/litespat* ~/.local/bin/litespat-connect ~/.local/bin/litespat-toggle \
      ~/.local/share/applications/litespat.desktop ~/Desktop/litespat.desktop
rm -rf ~/.local/share/litespat
systemctl --user daemon-reload
# optional: rm -rf ~/.config/litespat
```

---

## Known limitations

- **Starting at login, not at power-on.** The decoder needs a desktop to open its window.
  For unattended start-up, turn on automatic login.
- **Connections are made once.** If SuperCollider or the decoder restarts after a crash, or a
  source program starts later, run `systemctl --user restart litespat-connect` or toggle
  litespat off and on.
- **One saved decoder layout.** Switching rooms means re-importing the layout by hand.
- **Port pairing is positional.** The *n*-th matching source port goes to the *n*-th
  destination port, in JACK's listing order.
- **No per-source gain or mute over OSC yet**, and positions are not smoothed.

---

## Notes for maintainers

- **Zipping on Windows:** PowerShell 5.1's `Compress-Archive` writes backslashes in paths, so
  Linux unzips everything into one folder with names like `litespat\systemd\litespat.target`.
  Build the zip with Python's `zipfile` (or 7-Zip) instead. If a bad zip was already unzipped,
  fix it from inside the folder with:

  ```bash
  for f in litespat\\*; do n="${f#litespat\\}"; n="${n//\\//}"; mkdir -p "$(dirname "$n")"; mv -v "$f" "$n"; done
  ```

- **Changes from the original litespat script:** the `s.options.device = "litespat"` line is
  removed (on Linux it names a JACK server, which PipeWire doesn't have), as is `s.meter`
  (there's no window). `~hoa_panners` is reset on every run. The undefined `~input_GROUP`
  is replaced. OSC indices are range-checked. sclang exits with an error if the server
  fails to boot, so systemd can restart it.
- **Planned:** SuperMesh integration. Camera-tracked people would become litespat sources:
  a SuperCollider voice engine on this machine feeds `SOURCE_REGEX`, and the tracking master
  sends their positions to port 6666. A smoothed Cartesian `/source/xyz` message and a
  `/source/gain` message would make this cleaner.
