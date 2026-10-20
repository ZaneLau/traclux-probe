# Community preview limitations

The v0.2.0 preview has been tested with one five-probe run. The following issues remain open.

| Area | Current boundary |
| --- | --- |
| Initial real run | Anker / USB-C chargers, CSSM-HI generation, 5/5 responses saved, populated overview and brand performance |
| Question analysis | Responses are stored, but topic/prompt associations and grouped answer presentation are incomplete |
| Probe meaning | Generated questions do not always match their assigned topic labels; do not treat topic metrics as validated research |
| Summary insights | Tied rankings and positive headlines can overstate very small or zero-recommendation samples |
| Manual re-execution | A paused brand can be rejected as unschedulable; task startup also has a known transaction timing issue |
| Call accounting | A legacy foreign key can prevent call-log persistence; local credits are not authoritative provider billing |
| Providers | This run used Doubao for generation and sampling. Arbitrary compatible providers are not individually accepted |
| Web evidence | The example has no citations because sampling web search was off; generic compatible APIs do not automatically provide web search |
| Measurement | Generated probes are not real-user search logs; API answers are not consumer-client visibility |
| Security | Default deployment binds to loopback. See the dated [release security review](security-review.md); public multi-user hosting requires further security work |

The public demo displays saved questions, state tags and response excerpts. Response #4 is omitted because it contains unsupported brand and safety claims. New runs require a self-hosted instance.

Please report reproducible issues with the affected route, expected behavior and sanitized input.
