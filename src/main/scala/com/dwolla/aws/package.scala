package com.dwolla

import monix.newtypes.NewtypeWrapped

package object aws {
  type SecretId = SecretId.Type
}

package aws {
  object SecretId extends NewtypeWrapped[String]
}
