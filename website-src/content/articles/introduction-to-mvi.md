---
title: "Introduction to MVI"
date: 2020-08-12T10:33:07+01:00
draft: true
---

# Introduction to MVI

There are many great guides introducing MVI, so this will cover just the basics.

MVI has a few concepts:

* The state is immutable

This means that if you have a current state and know that something changed (e.g. you had a counter that just
incremented), you can't just update that property of the state. Instead, you need to create a new state and
push it down the view.

How do you achieve a immutable state?

1. Your state class (and the classes it uses) are immutable.

This means
