# Emerald Housing AWS network

Verified October 25, 2026. Shared network for emerald-housing and emerald-housing-frontend.

## Account and region

- Account: 660169747202
- Region: Sydney, ap-southeast-2. The Free plan project is assigned to Sydney; Mumbai was blocked by an organization policy. No plan upgrade was performed.
- VPC: emerald-practice-vpc, vpc-0b50e72049fd3714f
- CIDR: 10.20.0.0/16; no IPv6.
- DNS resolution and DNS hostnames: enabled.
- Internet gateway: igw-04aa113264a97c324, attached to this VPC.

## Subnets and explicit route associations

| Name | Subnet ID | Availability Zone | CIDR | Route table |
| --- | --- | --- | --- | --- |
| emerald-private-a | subnet-0103c4e91d452b82e | ap-southeast-2a | 10.20.11.0/24 | rtb-091cf99a93427522f |
| emerald-public-b | subnet-053400ca13395a26d | ap-southeast-2b | 10.20.2.0/24 | rtb-029fefb558ecb8587 |
| emerald-private-b | subnet-0031209944c89700c | ap-southeast-2b | 10.20.12.0/24 | rtb-0011a90eabf3f69e4 |
| emerald-public-a | subnet-0e94d77c7d4ea9d71 | ap-southeast-2a | 10.20.1.0/24 | rtb-029fefb558ecb8587 |

The two public subnets are reserved for a future ALB and Fargate tasks. The two private subnets are reserved for PostgreSQL and Redis; both AZs can be used in an RDS subnet group even for a Single-AZ database.

## Routes

- Public route table rtb-029fefb558ecb8587: 10.20.0.0/16 to local; 0.0.0.0/0 to igw-04aa113264a97c324.
- Private A rtb-091cf99a93427522f: 10.20.0.0/16 to local only.
- Private B rtb-0011a90eabf3f69e4: 10.20.0.0/16 to local only.
- Main route table rtb-0787310fea09ec204 remains local-only; all four subnets have explicit associations.
- No NAT gateways or VPC endpoints were created. Private subnets have no outbound internet route.

## Security groups

| Name | Group ID |
| --- | --- |
| emerald-redis-sg | sg-01a95f877430f66b7 |
| emerald-alb-sg | sg-00e44e70d61458276 |
| emerald-member-sg | sg-0591e1e33af35ffb9 |
| emerald-postgres-sg | sg-01f4d7a645e412171 |
| emerald-gateway-sg | sg-05bb08c9dbcae0e73 |
| emerald-society-sg | sg-0a2ff748a5aa6f913 |

### Inbound rules

| Destination | Port | Source |
| --- | --- | --- |
| emerald-redis-sg | TCP 6379 | emerald-gateway-sg (sg-05bb08c9dbcae0e73) |
| emerald-redis-sg | TCP 6379 | emerald-member-sg (sg-0591e1e33af35ffb9) |
| emerald-redis-sg | TCP 6379 | emerald-society-sg (sg-0a2ff748a5aa6f913) |
| emerald-alb-sg | TCP 80 | 36.255.109.45/32 |
| emerald-alb-sg | TCP 443 | 36.255.109.45/32 |
| emerald-member-sg | TCP 8080 | emerald-gateway-sg (sg-05bb08c9dbcae0e73) |
| emerald-postgres-sg | TCP 5432 | emerald-member-sg (sg-0591e1e33af35ffb9) |
| emerald-postgres-sg | TCP 5432 | emerald-society-sg (sg-0a2ff748a5aa6f913) |
| emerald-gateway-sg | TCP 8080 | emerald-alb-sg (sg-00e44e70d61458276) |
| emerald-gateway-sg | TCP 8080 | emerald-member-sg (sg-0591e1e33af35ffb9) |
| emerald-gateway-sg | TCP 8080 | emerald-society-sg (sg-0a2ff748a5aa6f913) |
| emerald-society-sg | TCP 8080 | emerald-gateway-sg (sg-05bb08c9dbcae0e73) |
| emerald-society-sg | TCP 8080 | emerald-member-sg (sg-0591e1e33af35ffb9) |

All six groups retain default allow-all IPv4 outbound. Application, PostgreSQL and Redis inbound rules use security group references only. Default network ACLs were retained. All three backend Dockerfiles expose container port 8080.

The initial ALB source is the public IP observed from this computer (36.255.109.45/32). Update it when the public/VPN egress IP changes. Review ALB access separately before using CloudFront.

## Deployment decisions

- No NAT gateway.
- Enable public IP assignment on Fargate tasks later to provide outbound access from public subnets; security groups restrict inbound traffic.
- No ALB, database, cache, ECS/Fargate service, or running workload was created in this step.
- No frontend repository changes are needed.
- Budget email verification remains unresolved; budget alerts do not stop resources automatically.

## Local CLI

    aws configure set region ap-southeast-2 --profile setup
    aws login --profile setup --region ap-southeast-2
    aws ec2 describe-subnets --filters Name=vpc-id,Values=vpc-0b50e72049fd3714f --profile setup --region ap-southeast-2
    aws ec2 describe-route-tables --filters Name=vpc-id,Values=vpc-0b50e72049fd3714f --profile setup --region ap-southeast-2
    aws ec2 describe-security-groups --filters Name=vpc-id,Values=vpc-0b50e72049fd3714f --profile setup --region ap-southeast-2
