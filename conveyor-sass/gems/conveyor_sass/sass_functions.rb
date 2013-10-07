module Sass::Script::Functions

  def asset_url(string)
    assert_type string, :String
    send_to_sass_helpers(:asset_url, string.value)
  end

  declare :asset_url, :args => [:string]

  def send_to_sass_helpers(method, arg)
    s = call_clj(arg) { |s| conveyor_sass_helpers.send(method, s) }
    Sass::Script::String.new("url(\"#{s}\")")
  end

  def conveyor_sass_helpers
    @conveyor_sass_helpers ||= JRClj.new "conveyor.sass.helpers"
  end

  def call_clj(s, &block)
    s = Zweikopf::Transformer.from_ruby(s)
    s = block.call(s)
    Zweikopf::Transformer.from_clj(s)
  end
end
