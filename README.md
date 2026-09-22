# litespat

**litespat** is a leightweight multichannel Ambisonics encoding tool, based on SuperCollider and the ATK-HOA extensions. 

It turns a predefined number of input audio signals (32 by default) into an AmbiX signal.
This version relies on external decoders (e.r. IEM) -- the encoded signal needs to be routed from the encoder to the decoder.

litespat listens to incoming OSC messages that allow the control of 32 virtual sound sources in spherical coordinates.


## Install & Setup

1) Install SuperCollider & the sc3-plugins

The sc3-plugins are necessary, because they contain the Ugens -- the server-side components that do the actual signal processing.

2) Install the ATK

Find the instructions here: https://github.com/ambisonictoolkit/atk-sc3

This installs the language-side components.


## Running the Script

The script litespat_server.sc can be run from the SuperCollider IDE, or via the terminal:

    $ sclang SuperCollider/litespat_server.sc


## Controlling Source Positions

The server listens to three OSC message types that address a single parameter of an individual source:

    /source/azim i f
    /source/elev i f
    /source/dist i f
    
For all three message types, the first argument is an integer defining the source to be controlled.
For azim and elev, the second argument is a float that represents the angle in radians.
For dist, the second argument is float that represents the distance in meters (approximated gain reduction).

There is a PD patch in the repo that shows how the control works.
