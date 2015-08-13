package models

import java.sql.Timestamp

/**
 * Created by pnagarjuna on 13/08/15.
 */
case class RefreshTime(accessToken: String, refreshToken: String, refreshTime: Timestamp, refreshPeriod: Long, userId: Long, id: Option[Long] = None)
