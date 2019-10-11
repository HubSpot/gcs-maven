# GCS Maven Wagon
This project is a Maven Wagon for Google Cloud Storage.  In order to to publish artifacts to a GCS bucket, the user (as identified by their access key) must be listed as an owner on the bucket.

## Usage
To publish Maven artifacts to GCS a build extension must be defined in a project's `pom.xml`.  

```xml
<project>
  ...
  <build>
    ...
    <extensions>
      ...
      <extension>
        <groupId>org.springframework.build</groupId>
        <artifactId>gcs-maven</artifactId>
        <version>5.0.0.RELEASE</version>
      </extension>
      ...
    </extensions>
    ...
  </build>
  ...
</project>
```

Once the build extension is configured distribution management repositories can be defined in the `pom.xml` with an `gcs://` scheme.

```xml
<project>
  ...
  <distributionManagement>
    <repository>
      <id>gcs-release</id>
      <name>GCS Release Repository</name>
      <url>gcs://<BUCKET>/release</url>
    </repository>
    <snapshotRepository>
      <id>gcs-snapshot</id>
      <name>GCS Snapshot Repository</name>
      <url>gcs://<BUCKET>/snapshot</url>
    </snapshotRepository>
  </distributionManagement>
  ...
</project>
```

Finally the `~/.m2/settings.xml` must be updated to include access and secret keys for the account. The access key should be used to populate the `username` element, and the secret access key should be used to populate the `password` element.

```xml
<settings>
  ...
  <servers>
    ...
    <server>
      <id>gcs-release</id>
      <username>0123456789ABCDEFGHIJ</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCD</password>
    </server>
    <server>
      <id>gcs-snapshot</id>
      <username>0123456789ABCDEFGHIJ</username>
      <password>0123456789abcdefghijklmnopqrstuvwxyzABCD</password>
    </server>
    ...
  </servers>
  ...
</settings>
```

Alternatively, the access and secret keys for the account can be provided using

* `GCS_ACCESS_KEY_ID` (or `GCS_ACCESS_KEY`) and `GCS_SECRET_KEY` (or `GCS_SECRET_ACCESS_KEY`) [environment variables][env-var]
* `gcs.accessKeyId` and `gcs.secretKey` [system properties][sys-prop]
* The Amazon EC2 [Instance Metadata Service][instance-metadata]

## Making Artifacts Public
This wagon doesn't set an explict ACL for each artfact that is uploaded.  Instead you should create an GCS Bucket Policy to set permissions on objects.  A bucket policy can be set in the [GCS Console][console] and can be generated using the [GCS Policy Generator][policy-generator].

In order to make the contents of a bucket public you need to add statements with the following details to your policy:

| Effect  | Principal | Action       | Google Resource Name (GRN)
| ------- | --------- | ------------ | --------------------------
| `Allow` | `*`       | `ListBucket` | `arn:gcs:s3:::<BUCKET>`
| `Allow` | `*`       | `GetObject`  | `arn:gcs:s3:::<BUCKET>/*`

If your policy is setup properly it should look something like:

```json
{
  "Id": "Policy1397027253868",
  "Statement": [
    {
      "Sid": "Stmt1397027243665",
      "Action": [
        "gcs:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": "arn:gcs:gcs:::<BUCKET>",
      "Principal": {
        "GCS": [
          "*"
        ]
      }
    },
    {
      "Sid": "Stmt1397027177153",
      "Action": [
        "gcs:GetObject"
      ],
      "Effect": "Allow",
      "Resource": "arn:gcs:gcs:::<BUCKET>/*",
      "Principal": {
        "GCS": [
          "*"
        ]
      }
    }
  ]
}
```

If you prefer to use the [command line][cli], you can use the following script to make the contents of a bucket public:

```bash
BUCKET=<BUCKET>
TIMESTAMP=$(date +%Y%m%d%H%M)
POLICY=$(cat<<EOF
{
  "Id": "public-read-policy-$TIMESTAMP",
  "Statement": [
    {
      "Sid": "list-bucket-$TIMESTAMP",
      "Action": [
        "gcs:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": "arn:gcs:gcs:::$BUCKET",
      "Principal": {
        "GCS": [
          "*"
        ]
      }
    },
    {
      "Sid": "get-object-$TIMESTAMP",
      "Action": [
        "gcs:GetObject"
      ],
      "Effect": "Allow",
      "Resource": "arn:gcs:gcs:::$BUCKET/*",
      "Principal": {
        "GCS": [
          "*"
        ]
      }
    }
  ]
}
EOF
)

gcs gcsapi put-bucket-policy --bucket $BUCKET --policy "$POLICY"
```
