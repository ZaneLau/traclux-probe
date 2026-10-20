# Community preview limitations

The owner chose to publish the existing system as an early community release after one real-model run, rather than delay for further workflow polishing. This is not a production-readiness certification.

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
| Security | Default deployment binds to loopback. See the dated [release security review](security-review.md); this preview is not approved for an untrusted public multi-user service |

The public demo is a standalone, read-only view of reviewed case fields. It does not bypass authentication to the self-hosted workbench, expose a live model key, or simulate a new live run. Answers are excerpts; one answer body was withheld from this public sample because it contained unsupported claims about independent brands. No replacement answer was invented.

Research, algorithm-superiority claims and premium features are outside this release. Use Issues for reproducible feedback, with secrets and customer data removed.
