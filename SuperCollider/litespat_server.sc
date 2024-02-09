



~n_inputs       = 32;
~hoa_order      = 5;
~n_hoa_channels = (pow(~hoa_order + 1.0 ,2.0)).asInteger;


s.options.device               = "litespat";
s.options.numInputBusChannels  = ~n_inputs;
s.options.numOutputBusChannels = ~n_hoa_channels;
s.options.memSize              = 65536;
s.options.numBuffers           = 4096;


s.waitForBoot({

	SynthDef(\hoa_mono_encoder,
		{
			|
			in_bus  = nil,
			out_bus = 0,
			azim    = 0,
			elev    = 0,
			dist    = 3,
			gain    = 1
			|

			var sound = gain * SoundIn.ar(in_bus);
			var level =  (1.0/(dist+1.0))*(1.0/(dist+1.0));
			var bform = HOASphericalHarmonics.coefN3D(~hoa_order, azim, elev) * sound * level;

			Out.ar(out_bus, bform);

	}).add;

	s.sync;

	/////////////////////////////////////////////////////////////////

	~spatial_GROUP = Group.after(~input_GROUP);
	s.sync;

	~ambi_BUS = Bus.audio(s, ~n_hoa_channels);

	for (0, ~n_inputs -1, {arg cnt;

		post('Adding HOA encoder module: ');
		cnt.postln;

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


	~output_GROUP	 = Group.after(~spatial_GROUP);
	s.sync;

	~hoa_output = {|gain=1| Out.ar(0 ,gain * In.ar(~ambi_BUS.index,~n_hoa_channels))}.play;
	s.sync;

	~hoa_output.set(\gain,0.5);
	~hoa_output.moveToTail(~output_GROUP);


});
