////////////////////////////////////////////////////////////////////////////////////////
// Basic settings for this script
////////////////////////////////////////////////////////////////////////////////////////

// port for listening to incoming OSC data
~osc_IN         = 6666;

// this determines how many sources (and inputs) we have
~n_inputs       = 32;

// the HOA order determines the size of the HOA bus and the nr of outputs
~hoa_order      = 5;
~n_hoa_channels = (pow(~hoa_order + 1.0 ,2.0)).asInteger;

////////////////////////////////////////////////////////////////////////////////////////
// Server options
////////////////////////////////////////////////////////////////////////////////////////

s.options.device               = "litespat";
s.options.numInputBusChannels  = ~n_inputs;
s.options.numOutputBusChannels = ~n_hoa_channels;
s.options.memSize              = 65536;
s.options.numBuffers           = 4096;

////////////////////////////////////////////////////////////////////////////////////////
// Start of main routine for setting up the spatial renderer
////////////////////////////////////////////////////////////////////////////////////////

s.waitForBoot({


	////////////////////////////////////////////////////////////////////////////////////
	// This is the SynthDef for the encoders

	SynthDef(\hoa_mono_encoder, {
		|
		in_bus  = 0,     // Defaulting to 0 for audio input
		out_bus = 0,
		azim    = 0,     // Azimuth (theta) in radians
		elev    = 0,     // Elevation (phi) in radians
		dist    = 1.5,   // Radius/Distance in meters (ATK references a default 1.5m)
		gain    = 1
		|

		var sound, bform;

		// 1. Capture and scale your input mono signal
		sound = SoundIn.ar(in_bus) * gain;

		// 2. Encode to B-format using ATK's directional encoder.
		bform = HoaEncodeDirection.ar(
			in: sound,
			theta: azim,
			phi: elev,
			radius: dist,
			order: ~hoa_order
		);

		// 3. Output the multi-channel B-format stream
		Out.ar(out_bus, bform);
	}).add;

	////////////////////////////////////////////////////////////////////////////////////////
	// use server sync after asynchronous commands
	s.sync;


	////////////////////////////////////////////////////////////////////////////////////////
	// The group for the spatial encoders
	~spatial_GROUP = Group.after(~input_GROUP);
	s.sync;

	////////////////////////////////////////////////////////////////////////////////////////
	// a multichannel audio bus for the encoded Ambisonics signal
	~ambi_BUS = Bus.audio(s, ~n_hoa_channels);


	////////////////////////////////////////////////////////////////////////////////////////
	// create all encoders in a loop
	for (0, ~n_inputs
		-1, {arg i;

			post('Adding HOA encoder module: ');
			i.postln;

			// this is the array of encoders
			~hoa_panners = ~hoa_panners.add(
				Synth(\hoa_mono_encoder,
					[
						\in_bus,  i,
						\out_bus, ~ambi_BUS.index
					],
					target: ~spatial_GROUP
			);)
	});
	s.sync;

	////////////////////////////////////////////////////////////////////////////////////////
	// Another group for the outputs
	////////////////////////////////////////////////////////////////////////////////////////

	~output_GROUP	 = Group.after(~spatial_GROUP);
	s.sync;

	////////////////////////////////////////////////////////////////////////////////////////
	// The output node
	////////////////////////////////////////////////////////////////////////////////////////

	~hoa_output = {|gain=1| Out.ar(0 ,gain * In.ar(~ambi_BUS.index,~n_hoa_channels))}.play;
	s.sync;
	// goes into the output group
	~hoa_output.moveToTail(~output_GROUP);
	~hoa_output.set(\gain,0.75);


	////////////////////////////////////////////////////////////////////////////////////////
	// One OSC listener function for each spatial paramter
	////////////////////////////////////////////////////////////////////////////////////////

	OSCdef('/source/azim',
		{
			arg msg, time, addr, recvPort;
			var azim = msg[2];

			~hoa_panners[msg[1]].set(\azim, azim);
			postln("Azimuth: "+azim)

	},'/source/azim');

	OSCdef('/source/elev',
		{
			arg msg, time, addr, recvPort;
			var elev = msg[2];

			~hoa_panners[msg[1]].set(\elev,elev);
			postln("Elevation: "+elev)

	}, '/source/elev');

	OSCdef('/source/dist',
		{
			arg msg, time, addr, recvPort;
			var dist = msg[2];

			~hoa_panners[msg[1]].set(\dist,dist);
			postln("Distance: "+dist)

	}, '/source/dist');


	// open our extra ports for OSC and give feedback
	thisProcess.openUDPPort(~osc_IN);
	postln("Listening for OSC on ports: "++thisProcess.openPorts);

	s.meter;
});
