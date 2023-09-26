gen: vex100.v

Vex300.v: src/main/scala/vexriscv/demo/Vex300.scala
	sbt "runMain vexriscv.demo.Vex300"

vex100.v: src/main/scala/vexriscv/demo/Vex100.scala
	sbt "runMain vexriscv.demo.Vex100"
