<template>
  <div id="app">
    <div id="myDiagramDiv"></div>
  </div>
</template>
<script>
import go from 'gojs'

export default {
  name: 'NetworkOverviewGoJs',
  mounted () {
    const $ = go.GraphObject.make
    const myDiagram = $(
      go.Diagram,
      'myDiagramDiv', // create a Diagram for the DIV HTML element
      {
        // enable undo & redo
        'undoManager.isEnabled': true
      }
    )

    myDiagram.nodeTemplateMap.add(
      'Generator',
      $(
        go.Node,
        'Spot',
        { locationSpot: go.Spot.Center, selectionObjectName: 'BODY' },
        new go.Binding('location', 'location', go.Point.parse).makeTwoWay(
          go.Point.stringify
        ),
        $(go.Picture, '../../assets/icons/router.png', {
          source: '../../assets/icons/router.png',
          name: 'router',
          width: 40,
          height: 40,
          margin: 5,
          portId: '',
          fromLinkable: true,
          cursor: 'pointer'
        }),
        $(
          go.TextBlock,
          {
            alignment: go.Spot.Right,
            alignmentFocus: go.Spot.Left,
            editable: true
          },
          'Router',
          new go.Binding('text').makeTwoWay()
        )
      )
    )

    myDiagram.nodeTemplateMap.add(
      'Connector',
      $(
        go.Node,
        'Spot',
        { locationSpot: go.Spot.Center, selectionObjectName: 'BODY' },
        new go.Binding('location', 'location', go.Point.parse).makeTwoWay(
          go.Point.stringify
        ),
        $(go.Shape, 'Circle', {
          name: 'BODY',
          stroke: null,
          fill: $(go.Brush, 'Linear', { 0: 'lightgray', 1: 'gray' }),
          background: 'gray',
          width: 20,
          height: 20,
          margin: 2,
          portId: '',
          fromLinkable: true,
          cursor: 'pointer',
          fromSpot: go.Spot.TopBottomSides,
          toSpot: go.Spot.TopBottomSides
        }),
        $(
          go.TextBlock,
          {
            alignment: go.Spot.Right,
            alignmentFocus: go.Spot.Left,
            editable: true
          },
          new go.Binding('text').makeTwoWay()
        )
      )
    )

    myDiagram.nodeTemplateMap.add(
      'Consumer',
      $(
        go.Node,
        'Spot',
        {
          locationSpot: go.Spot.Center,
          locationObjectName: 'BODY',
          selectionObjectName: 'BODY'
        },
        new go.Binding('location', 'location', go.Point.parse).makeTwoWay(
          go.Point.stringify
        ),
        $(go.Shape, 'Rectangle', {
          name: 'BODY',
          width: 50,
          height: 40,
          margin: 2,
          portId: '',
          fromLinkable: false,
          cursor: 'pointer',
          fromSpot: go.Spot.TopBottomSides,
          toSpot: go.Spot.TopBottomSides
        }),
        $(
          go.TextBlock,
          {
            alignment: go.Spot.Right,
            alignmentFocus: go.Spot.Left,
            editable: true
          },
          new go.Binding('text').makeTwoWay()
        )
      )
    )

    myDiagram.nodeTemplateMap.add(
      'HBar',
      $(
        go.Node,
        'Spot',
        new go.Binding('location', 'location', go.Point.parse).makeTwoWay(
          go.Point.stringify
        ),
        {
          layerName: 'Background',
          // special resizing: just at the ends
          resizable: true,
          resizeObjectName: 'SHAPE',
          resizeAdornmentTemplate: $(
            go.Adornment,
            'Spot',
            $(go.Placeholder),
            $(
              go.Shape, // left resize handle
              {
                alignment: go.Spot.Left,
                cursor: 'col-resize',
                desiredSize: new go.Size(6, 6),
                fill: 'lightblue',
                stroke: 'dodgerblue'
              }
            ),
            $(
              go.Shape, // right resize handle
              {
                alignment: go.Spot.Right,
                cursor: 'col-resize',
                desiredSize: new go.Size(6, 6),
                fill: 'lightblue',
                stroke: 'dodgerblue'
              }
            )
          )
        },
        $(
          go.Shape,
          'Rectangle',
          {
            name: 'SHAPE',
            fill: 'yellow',
            stroke: null,
            strokeWidth: 0,
            width: 1000,
            height: 4,
            minSize: new go.Size(100, 4),
            maxSize: new go.Size(Infinity, 4)
          },
          new go.Binding('desiredSize', 'size', go.Size.parse).makeTwoWay(
            go.Size.stringify
          ),
          new go.Binding('fill'),
          { portId: '', toLinkable: true }
        ),
        $(
          go.TextBlock,
          {
            alignment: go.Spot.Right,
            alignmentFocus: go.Spot.Left,
            editable: true
          },
          new go.Binding('text').makeTwoWay()
        )
      )
    )

    class BarLink extends go.Link {
      computeSpot (from, port) {
        if (from && this.toNode && this.toNode.category === 'HBar') {
          return go.Spot.TopBottomSides
        }
        if (!from && this.fromNode && this.fromNode.category === 'HBar') {
          return go.Spot.TopBottomSides
        }
        return super.computeSpot(from, port)
      }

      getLinkPoint (node, port, spot, from, ortho, othernode, otherport) {
        if (!from && node.category === 'HBar') {
          var op = super.getLinkPoint(
            othernode,
            otherport,
            this.computeSpot(!from),
            !from,
            ortho,
            node,
            port
          )
          var r = port.getDocumentBounds()
          var y = op.y > r.centerY ? r.bottom : r.top
          if (op.x < r.left) return new go.Point(r.left, y)
          if (op.x > r.right) return new go.Point(r.right, y)
          return new go.Point(op.x, y)
        } else {
          return super.getLinkPoint(
            node,
            port,
            spot,
            from,
            ortho,
            othernode,
            otherport
          )
        }
      }

      getLinkDirection (
        node,
        port,
        linkpoint,
        spot,
        from,
        ortho,
        othernode,
        otherport
      ) {
        if (node.category === 'HBar' || othernode.category === 'HBar') {
          var p = port.getDocumentPoint(go.Spot.Center)
          var op = otherport.getDocumentPoint(go.Spot.Center)
          var below = op.y > p.y
          return below ? 90 : 270
        } else {
          return super.getLinkDirection(
            node,
            port,
            linkpoint,
            spot,
            from,
            ortho,
            othernode,
            otherport
          )
        }
      }
    }

    myDiagram.linkTemplate = $(
      BarLink, // subclass defined below
      {
        routing: go.Link.Orthogonal,
        relinkableFrom: true,
        relinkableTo: true,
        toPortChanged: (link, oldport, newport) => {
          if (newport instanceof go.Shape) link.path.stroke = newport.fill
        }
      },
      $(go.Shape, { strokeWidth: 2 })
    )

    // define a simple Node template
    myDiagram.nodeTemplate = $(
      go.Node,
      'Auto', // the Shape will go around the TextBlock
      $(
        go.Shape,
        'Circle',
        { strokeWidth: 0, fill: 'white' }, // default fill is white
        // Shape.fill is bound to Node.data.color
        new go.Binding('fill', 'color')
      ),
      $(
        go.TextBlock,
        { margin: 8 }, // some room around the text
        // TextBlock.text is bound to Node.data.key
        new go.Binding('text', 'key')
      )
    )

    myDiagram.model = new go.GraphLinksModel(
      [
        { key: 0, text: 'Gen1', category: 'Generator', location: '300 0' },
        {
          key: 1,
          text: 'Bar1',
          category: 'HBar',
          location: '100 100',
          size: '500 4',
          fill: 'green'
        },
        { key: 3, text: 'Cons1', category: 'Consumer', location: '53 234' },
        {
          key: 2,
          text: 'Bar2',
          category: 'HBar',
          location: '0 300',
          size: '600 4',
          fill: 'orange'
        },
        {
          key: 4,
          text: 'Conn1',
          category: 'Connector',
          location: '232.5 207.75'
        },
        {
          key: 5,
          text: 'Cons3',
          category: 'Consumer',
          location: '357.5 230.75'
        },
        {
          key: 6,
          text: 'Cons2',
          category: 'Consumer',
          location: '484.5 164.75'
        }
      ],
      [
        { from: 0, to: 1 },
        { from: 0, to: 2 },
        { from: 3, to: 2 },
        { from: 4, to: 1 },
        { from: 4, to: 2 },
        { from: 5, to: 2 },
        { from: 6, to: 1 }
      ]
    )
  },
  methods: {
    init () {
    }
  }
}
</script>
<style scoped>
#myDiagramDiv {
  width: 100%;
  height: 500px;
}
</style>
