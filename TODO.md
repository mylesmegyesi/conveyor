* rethink api
  * get-asset (returns everything if found, nil if not found)
  * get-asset! (returns everything if found, throws exception if not found)
  * get-asset-path (returns logical-path or digest-path depending on config, nil if not found)
  * get-asset-path! (returns logical-path or digest-path depending on config, throws if not found)
* precompile with regex
* compress
* dependcies (require)
* concat dependencies together
