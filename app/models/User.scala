package models

/**
 * Created by pnagarjuna on 13/08/15.
 */
case class User(host: String, email: String, pass: String, id: Option[Long] = None)

