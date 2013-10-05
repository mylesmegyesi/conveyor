#### Added by Myles
dir = File.dirname(__FILE__)
$LOAD_PATH.unshift dir unless $LOAD_PATH.include?(dir)
######

require "zweikopf/version"

require "zweikopf/primitive"
require "zweikopf/keyword"
require "zweikopf/hash"
require "zweikopf/array"
require "zweikopf/transformer"


module Zweikopf

end
