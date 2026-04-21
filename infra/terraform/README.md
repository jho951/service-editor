# Documents Service Terraform

This directory keeps the deployment infrastructure entrypoint aligned with the other backend services.

```bash
terraform init
terraform plan -var-file=terraform.tfvars
```

Copy `terraform.tfvars.example` to `terraform.tfvars` and fill environment-specific values before applying.
