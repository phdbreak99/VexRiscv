package vexriscv.demo

import vexriscv.plugin._
import vexriscv.ip.{DataCacheConfig, InstructionCacheConfig}
import vexriscv.{plugin, VexRiscv, VexRiscvConfig}
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi.Axi4ReadOnly
import spinal.lib.com.jtag.Jtag

object Vex100{
  def main(args: Array[String]) {
    val report = SpinalVerilog{
      val cpuConfig = VexRiscvConfig(
        plugins = List(
          new IBusCachedPlugin(
            resetVector = 0x10000000l,
            relaxedPcCalculation = false,
            prediction = DYNAMIC_TARGET,
            historyRamSizeLog2 = 8,
            config = InstructionCacheConfig(
              cacheSize = 1024*16,
              bytePerLine =32,
              wayCount = 4,
              addressWidth = 32,
              cpuDataWidth = 32,
              memDataWidth = 32,
              catchIllegalAccess = true,
              catchAccessFault = true,
              asyncTagMemory = false,
              twoCycleRam = false,
              twoCycleCache = true
            )
          ),
          new DBusCachedPlugin(
            config = new DataCacheConfig(
              cacheSize         = 1024*16,
              bytePerLine       = 32,
              wayCount          = 4,
              addressWidth      = 32,
              cpuDataWidth      = 32,
              memDataWidth      = 32,
              catchAccessError  = true,
              catchIllegal      = true,
              catchUnaligned    = true
            )
          ),
          new StaticMemoryTranslatorPlugin(
            ioRange      = _(31 downto 28) === U"111" /* [0xF000_0000, 0xFFFF_FFFF] */
          ),
          new DecoderSimplePlugin(
            catchIllegalInstruction = true
          ),
          new RegFilePlugin(
            regFileReadyKind = plugin.SYNC,
            zeroBoot = false
          ),
          new IntAluPlugin,
          new SrcPlugin(
            separatedAddSub = false,
            executeInsertion = true
          ),
          new FullBarrelShifterPlugin,
          new HazardSimplePlugin(
            bypassExecute           = true,
            bypassMemory            = true,
            bypassWriteBack         = true,
            bypassWriteBackBuffer   = true,
            pessimisticUseSrc       = false,
            pessimisticWriteRegFile = false,
            pessimisticAddressMatch = false
          ),
          new MulPlugin,
          new DivPlugin,
          new CsrPlugin(CsrPluginConfig.small),
          new DebugPlugin(ClockDomain.current.clone(reset = Bool().setName("debug_reset"))),
          new BranchPlugin(
            earlyBranch = false,
            catchAddressMisaligned = true
          ),
          new YamlPlugin("vex100.yaml")
        )
      )

      val cpu = new VexRiscv(cpuConfig)
      cpu.setDefinitionName("vex100")
      cpu.rework {
        var iBus : Axi4ReadOnly = null
        for (plugin <- cpuConfig.plugins) plugin match {
          case plugin: IBusSimplePlugin => {
            plugin.iBus.setAsDirectionLess() //Unset IO properties of iBus
            iBus = master(plugin.iBus.toAxi4ReadOnly().toFullConfig())
              .setName("insn_axi")
              .addTag(ClockDomainTag(ClockDomain.current)) //Specify a clock domain to the iBus (used by QSysify)
          }
          case plugin: IBusCachedPlugin => {
            plugin.iBus.setAsDirectionLess() //Unset IO properties of iBus
            iBus = master(plugin.iBus.toAxi4ReadOnly().toFullConfig())
              .setName("insn_axi")
              .addTag(ClockDomainTag(ClockDomain.current)) //Specify a clock domain to the iBus (used by QSysify)
          }
          case plugin: DBusSimplePlugin => {
            plugin.dBus.setAsDirectionLess()
            master(plugin.dBus.toAxi4Shared().toAxi4().toFullConfig())
              .setName("data_axi")
          }
          case plugin: DBusCachedPlugin => {
            plugin.dBus.setAsDirectionLess()
            master(plugin.dBus.toAxi4Shared().toAxi4().toFullConfig())
              .setName("data_axi")
          }
          case plugin: DebugPlugin => plugin.debugClockDomain {
            plugin.io.bus.setAsDirectionLess()
            val jtag = slave(new Jtag()).setName("jtag")
            jtag <> plugin.io.bus.fromJtag()
            plugin.io.resetOut.setName("debug_reset_out")
          }
          case plugin : CsrPlugin => {
            plugin.externalInterrupt.setName("interrupt_external")
            plugin.timerInterrupt.setName("interrupt_timer")
            plugin.softwareInterrupt.setName("interrupt_software")
          }
          case _ =>
        }
      }

      cpu
    }
  }
}
