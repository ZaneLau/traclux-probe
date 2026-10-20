# Security policy / 安全报告

Please report vulnerabilities privately through [GitHub's private vulnerability reporting](https://github.com/ZaneLau/traclux-probe/security/advisories/new). Do not include secrets, personal information or customer data in public issues, sample files or pull requests.

请通过上面的 GitHub 私密漏洞报告入口提交安全问题。普通功能问题可以使用 Issues；请勿公开上传密钥、个人信息和客户资料。

The current 0.2.x community preview line is supported. Include the version, a minimal fictional input and reproduction steps. Reports can contain sensitive material: the HTML export includes the entire supplied candidate pool and imported answers. Review it before sharing.

The default Compose binds the web app to loopback and does not publish database or backend ports. Model calls use your credentials. Do not expose this preview to untrusted public users. See [release security review](docs/community/security-review.md) for what was checked and what remains.
