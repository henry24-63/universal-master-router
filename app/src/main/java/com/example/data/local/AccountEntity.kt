package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_accounts")
data class AccountEntity(
  @PrimaryKey val id: String,
  val label: String,
  val endpoint: String,
  val region: String,
  val status: String,
  val projectsCount: Int,
  val lastVerified: String
)
